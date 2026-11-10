// Package app: use case tải file trực tiếp lên object storage bằng URL ký sẵn (presigned PUT).
// Trình duyệt không gửi file qua service: xin URL → PUT lên storage → gọi complete để service xác minh.
package app

import (
	"context"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/domain"
)

type Repository interface {
	Create(ctx context.Context, m domain.Media) error
	Get(ctx context.Context, id string) (domain.Media, error)
	MarkReady(ctx context.Context, id string, at time.Time) error
	Delete(ctx context.Context, id string) error
	ListByOwner(ctx context.Context, owner string, limit int) ([]domain.Media, error)
	StalePending(ctx context.Context, before time.Time, limit int) ([]domain.Media, error)
}

type ObjectInfo struct {
	Size        int64
	ContentType string
}

type Storage interface {
	PresignPut(ctx context.Context, key, contentType string, size int64, ttl time.Duration) (string, map[string]string, error)
	Stat(ctx context.Context, key string) (ObjectInfo, bool, error)
	Remove(ctx context.Context, key string) error
	PublicURL(key string) string
}

type IDs interface{ NewID() string }

type Options struct {
	UploadTTL  time.Duration // hạn của URL ký sẵn
	PendingTTL time.Duration // PENDING quá hạn này bị dọn
}

type Service struct {
	repo  Repository
	store Storage
	ids   IDs
	opt   Options
	now   func() time.Time
}

func NewService(r Repository, s Storage, ids IDs, o Options) *Service {
	if o.UploadTTL <= 0 {
		o.UploadTTL = 15 * time.Minute
	}
	if o.PendingTTL <= 0 {
		o.PendingTTL = 24 * time.Hour
	}
	return &Service{repo: r, store: s, ids: ids, opt: o, now: time.Now}
}

type Upload struct {
	Media     domain.Media      `json:"media"`
	UploadURL string            `json:"uploadUrl"`
	Headers   map[string]string `json:"headers"`
	ExpiresAt time.Time         `json:"expiresAt"`
}

func (s *Service) RequestUpload(ctx context.Context, r domain.UploadRequest) (Upload, error) {
	if err := r.Validate(); err != nil {
		return Upload{}, err
	}
	now := s.now().UTC()
	id := s.ids.NewID()
	m := domain.Media{
		ID: id, OwnerID: r.OwnerID, Purpose: r.Purpose, FileName: domain.SafeFileName(r.FileName),
		ContentType: r.ContentType, SizeBytes: r.SizeBytes, Status: domain.StatusPending, CreatedAt: now,
		ObjectKey: domain.ObjectKey(r.Purpose, id, r.ContentType, now),
	}
	if err := s.repo.Create(ctx, m); err != nil {
		return Upload{}, err
	}
	url, headers, err := s.store.PresignPut(ctx, m.ObjectKey, m.ContentType, m.SizeBytes, s.opt.UploadTTL)
	if err != nil {
		return Upload{}, err
	}
	return Upload{Media: m, UploadURL: url, Headers: headers, ExpiresAt: now.Add(s.opt.UploadTTL)}, nil
}

// Complete xác minh object đã có trên storage, đúng dung lượng và loại đã khai báo, rồi chuyển READY.
func (s *Service) Complete(ctx context.Context, id, subject string, admin bool) (domain.Media, error) {
	m, err := s.repo.Get(ctx, id)
	if err != nil {
		return m, err
	}
	if !m.CanManage(subject, admin) {
		return m, apperr.NotFound("media %s not found", id)
	}
	if m.Status == domain.StatusReady {
		return s.withURL(m), nil
	}
	info, ok, err := s.store.Stat(ctx, m.ObjectKey)
	if err != nil {
		return m, apperr.Unavailable("object storage unavailable")
	}
	if !ok {
		return m, apperr.Conflict("file has not been uploaded yet")
	}
	if info.Size != m.SizeBytes || (info.ContentType != "" && info.ContentType != m.ContentType) {
		_ = s.store.Remove(ctx, m.ObjectKey)
		_ = s.repo.Delete(ctx, m.ID)
		return m, apperr.Invalid("uploaded file does not match the declared size or content type")
	}
	at := s.now().UTC()
	if err := s.repo.MarkReady(ctx, id, at); err != nil {
		return m, err
	}
	m.Status, m.CompletedAt = domain.StatusReady, &at
	return s.withURL(m), nil
}

func (s *Service) withURL(m domain.Media) domain.Media {
	if m.Status == domain.StatusReady {
		m.URL = s.store.PublicURL(m.ObjectKey)
	}
	return m
}

func (s *Service) Get(ctx context.Context, id string) (domain.Media, error) {
	m, err := s.repo.Get(ctx, id)
	if err != nil {
		return m, err
	}
	if m.Status != domain.StatusReady {
		return m, apperr.NotFound("media %s not found", id)
	}
	return s.withURL(m), nil
}

func (s *Service) ListMine(ctx context.Context, owner string, limit int) ([]domain.Media, error) {
	if limit <= 0 || limit > 100 {
		limit = 50
	}
	list, err := s.repo.ListByOwner(ctx, owner, limit)
	for i := range list {
		list[i] = s.withURL(list[i])
	}
	return list, err
}

func (s *Service) Delete(ctx context.Context, id, subject string, admin bool) error {
	m, err := s.repo.Get(ctx, id)
	if err != nil {
		return err
	}
	if !m.CanManage(subject, admin) {
		return apperr.NotFound("media %s not found", id)
	}
	if err := s.store.Remove(ctx, m.ObjectKey); err != nil {
		return apperr.Unavailable("object storage unavailable")
	}
	return s.repo.Delete(ctx, id)
}

// CleanupPending xoá file tải dở (PENDING quá hạn) cả trên storage lẫn DB; trả số bản ghi đã dọn.
func (s *Service) CleanupPending(ctx context.Context) (int, error) {
	stale, err := s.repo.StalePending(ctx, s.now().Add(-s.opt.PendingTTL), 500)
	if err != nil {
		return 0, err
	}
	n := 0
	for _, m := range stale {
		if err := s.store.Remove(ctx, m.ObjectKey); err != nil {
			return n, err
		}
		if err := s.repo.Delete(ctx, m.ID); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}
