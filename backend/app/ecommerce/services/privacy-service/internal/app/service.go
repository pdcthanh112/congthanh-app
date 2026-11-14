// Package app chứa use case của privacy-service.
package app

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"log/slog"
	"strings"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/domain"
)

// ReminderInterval: processor chưa trả lời thì phát lại yêu cầu sau khoảng này (consumer idempotent).
const ReminderInterval = 7 * 24 * time.Hour

// IdentityProcessor là tên task của Keycloak trong registry; privacy-service tự xử lý khi có Identity.
const IdentityProcessor = "keycloak"

type Service struct {
	identity      Identity
	store         Store
	ids           IDs
	processors    []string
	policyVersion string
	now           func() time.Time
}

func NewService(store Store, ids IDs, processors []string, policyVersion string) *Service {
	return &Service{store: store, ids: ids, processors: processors, policyVersion: policyVersion, now: time.Now}
}

// WithIdentity bật xoá tài khoản Keycloak tự động.
func (s *Service) WithIdentity(id Identity) *Service {
	s.identity = id
	return s
}

// CloseIdentity xoá tài khoản cho các yêu cầu DELETE mà chỉ còn task keycloak (mọi service khác đã xoá xong),
// rồi đánh dấu task. Xoá tài khoản sau cùng để khách vẫn đăng nhập xem được tiến độ trong lúc chờ.
func (s *Service) CloseIdentity(ctx context.Context) (int, error) {
	if s.identity == nil {
		return 0, nil
	}
	open, err := s.store.RequestsByStatus(ctx, domain.StatusInProgress, 200)
	if err != nil {
		return 0, err
	}
	n := 0
	for i := range open {
		r := open[i]
		pending := r.PendingProcessors()
		if r.Type != domain.RequestDelete || len(pending) != 1 || pending[0] != IdentityProcessor {
			continue
		}
		if err := s.identity.DeleteUser(ctx, r.SubjectID); err != nil {
			slog.WarnContext(ctx, "keycloak user deletion failed", "request", r.ID, "err", err)
			continue
		}
		if err := s.ProcessorReply(ctx, r.ID, IdentityProcessor, false, "", ""); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

// Consents trả về lựa chọn hiệu lực theo từng mục đích. gpc = trình duyệt gửi Sec-GPC: 1: luật CA/CO coi đó là
// yêu cầu opt-out hợp lệ cho bán/chia sẻ và quảng cáo theo hành vi, nên ghi nhận ngay nếu chưa opt-out.
func (s *Service) Consents(ctx context.Context, subject string, gpc bool) ([]domain.Consent, error) {
	latest, err := s.store.LatestConsents(ctx, subject)
	if err != nil {
		return nil, err
	}
	if gpc {
		var changes []domain.Consent
		for _, p := range []domain.Purpose{domain.PurposeSaleSharing, domain.PurposeTargetedAds} {
			if c, ok := latest[p]; !ok || c.Granted {
				changes = append(changes, domain.Consent{SubjectID: subject, Purpose: p, Granted: false, Source: domain.SourceGPC})
			}
		}
		if len(changes) > 0 {
			saved, err := s.record(ctx, changes)
			if err != nil {
				return nil, err
			}
			for _, c := range saved {
				latest[c.Purpose] = c
			}
		}
	}
	return domain.EffectiveConsents(subject, latest), nil
}

// SetConsent ghi một lựa chọn của khách.
func (s *Service) SetConsent(ctx context.Context, subject, purpose string, granted bool, source string) (domain.Consent, error) {
	p, err := domain.ParsePurpose(purpose)
	if err != nil {
		return domain.Consent{}, err
	}
	src, err := domain.ParseSource(source)
	if err != nil {
		return domain.Consent{}, err
	}
	saved, err := s.record(ctx, []domain.Consent{{SubjectID: subject, Purpose: p, Granted: granted, Source: src}})
	if err != nil {
		return domain.Consent{}, err
	}
	return saved[0], nil
}

func (s *Service) record(ctx context.Context, cs []domain.Consent) ([]domain.Consent, error) {
	now := s.now().UTC()
	err := s.store.InTx(ctx, func(tx Tx) error {
		for i := range cs {
			cs[i].RecordedAt, cs[i].PolicyVersion = now, s.policyVersion
			if err := tx.AppendConsent(ctx, cs[i]); err != nil {
				return err
			}
			if err := tx.Emit(ctx, domain.ConsentChanged{Consent: cs[i]}); err != nil {
				return err
			}
		}
		return nil
	})
	return cs, err
}

// OpenRequest mở yêu cầu DSAR cho chính người đang đăng nhập (đăng nhập Keycloak là bước xác minh danh tính).
// email (tuỳ chọn) chỉ được lưu dạng SHA-256 để service tìm dữ liệu theo email mà không lộ email.
func (s *Service) OpenRequest(ctx context.Context, subject, requestType, email string) (*domain.Request, error) {
	t, err := domain.ParseRequestType(requestType)
	if err != nil {
		return nil, err
	}
	now := s.now().UTC()
	r, err := domain.NewRequest(s.ids.New(), subject, t, HashEmail(email), s.processors, now)
	if err != nil {
		return nil, err
	}
	err = s.store.InTx(ctx, func(tx Tx) error {
		open, err := tx.OpenRequestOfType(ctx, subject, t)
		if err != nil {
			return err
		}
		if open {
			return apperr.Conflict("a %s request is already in progress", t)
		}
		if err := tx.InsertRequest(ctx, r); err != nil {
			return err
		}
		events := []domain.Event{domain.RequestOpened{Request: *r}}
		switch t {
		case domain.RequestDelete:
			events = append(events, domain.ErasureRequested{Request: *r})
		case domain.RequestAccess:
			events = append(events, domain.AccessExportRequested{Request: *r})
		case domain.RequestOptOut:
			for _, p := range []domain.Purpose{domain.PurposeSaleSharing, domain.PurposeTargetedAds} {
				c := domain.Consent{SubjectID: subject, Purpose: p, Granted: false, Source: domain.SourceRequest,
					PolicyVersion: s.policyVersion, RecordedAt: now}
				if err := tx.AppendConsent(ctx, c); err != nil {
					return err
				}
				events = append(events, domain.ConsentChanged{Consent: c})
			}
			events = append(events, domain.RequestCompleted{Request: *r})
		}
		return tx.Emit(ctx, events...)
	})
	if err != nil {
		return nil, err
	}
	return r, nil
}

// MyRequests liệt kê yêu cầu của khách.
func (s *Service) MyRequests(ctx context.Context, subject string) ([]domain.Request, error) {
	return s.store.RequestsBySubject(ctx, subject, 50)
}

// GetRequest: khách chỉ xem được yêu cầu của mình; người khác nhận NotFound để không lộ sự tồn tại.
func (s *Service) GetRequest(ctx context.Context, id, caller string, staff bool) (*domain.Request, error) {
	r, err := s.store.GetRequest(ctx, id)
	if err != nil {
		return nil, err
	}
	if !staff && r.SubjectID != caller {
		return nil, apperr.NotFound("request %s not found", id)
	}
	return r, nil
}

func (s *Service) RequestsByStatus(ctx context.Context, status string, limit int) ([]domain.Request, error) {
	st := domain.RequestStatus(strings.ToUpper(status))
	switch st {
	case domain.StatusInProgress, domain.StatusCompleted, domain.StatusPartiallyCompleted, domain.StatusRejected:
	default:
		return nil, apperr.Invalid("unknown status %q", status)
	}
	return s.store.RequestsByStatus(ctx, st, min(max(limit, 1), 500))
}

// ProcessorReply ghi kết quả một processor gửi về (ErasureCompleted / AccessExportReady). Idempotent.
func (s *Service) ProcessorReply(ctx context.Context, requestID, processor string, retained bool, reason, objectKey string) error {
	return s.store.InTx(ctx, func(tx Tx) error {
		r, err := tx.LockRequest(ctx, requestID)
		if err != nil {
			if apperr.KindOf(err) == apperr.KindNotFound {
				slog.WarnContext(ctx, "reply for unknown privacy request", "request", requestID, "processor", processor)
				return nil
			}
			return err
		}
		done, err := r.Complete(processor, retained, reason, objectKey, s.now().UTC())
		if err != nil {
			slog.WarnContext(ctx, "rejected processor reply", "request", requestID, "processor", processor, "err", err)
			return nil
		}
		if err := tx.UpdateRequest(ctx, r); err != nil {
			return err
		}
		if done {
			return tx.Emit(ctx, domain.RequestCompleted{Request: *r})
		}
		return nil
	})
}

// CompleteTaskManually: nhân viên xác nhận phần việc của hệ thống ngoài (Stripe, nhà cung cấp email...).
func (s *Service) CompleteTaskManually(ctx context.Context, requestID, processor string, retained bool, reason string) error {
	return s.store.InTx(ctx, func(tx Tx) error {
		r, err := tx.LockRequest(ctx, requestID)
		if err != nil {
			return err
		}
		done, err := r.Complete(processor, retained, reason, "", s.now().UTC())
		if err != nil {
			return err
		}
		if err := tx.UpdateRequest(ctx, r); err != nil {
			return err
		}
		if done {
			return tx.Emit(ctx, domain.RequestCompleted{Request: *r})
		}
		return nil
	})
}

func (s *Service) Extend(ctx context.Context, requestID, reason string) (*domain.Request, error) {
	var out *domain.Request
	err := s.store.InTx(ctx, func(tx Tx) error {
		r, err := tx.LockRequest(ctx, requestID)
		if err != nil {
			return err
		}
		if err := r.Extend(reason); err != nil {
			return err
		}
		out = r
		return tx.UpdateRequest(ctx, r)
	})
	return out, err
}

// Sweep phát lại yêu cầu cho processor chưa trả lời và cảnh báo yêu cầu quá hạn. Chạy định kỳ.
func (s *Service) Sweep(ctx context.Context) (reminded, overdue int, err error) {
	now := s.now().UTC()
	stale, err := s.store.StaleOpenRequests(ctx, now.Add(-ReminderInterval), 200)
	if err != nil {
		return 0, 0, err
	}
	for i := range stale {
		r := stale[i]
		if r.Overdue(now) {
			overdue++
			slog.ErrorContext(ctx, "privacy request overdue", "request", r.ID, "type", r.Type, "due", r.DueAt, "pending", r.PendingProcessors())
		}
		var ev domain.Event
		switch r.Type {
		case domain.RequestDelete:
			ev = domain.ErasureRequested{Request: r}
		case domain.RequestAccess:
			ev = domain.AccessExportRequested{Request: r}
		default:
			continue
		}
		if err := s.store.InTx(ctx, func(tx Tx) error {
			if err := tx.Emit(ctx, ev); err != nil {
				return err
			}
			return tx.MarkReminded(ctx, r.ID, now)
		}); err != nil {
			return reminded, overdue, err
		}
		reminded++
	}
	return reminded, overdue, nil
}

// RunSweeper chạy Sweep mỗi giờ cho tới khi ctx bị huỷ.
func (s *Service) RunSweeper(ctx context.Context) error {
	t := time.NewTicker(time.Hour)
	defer t.Stop()
	for {
		if n, err := s.CloseIdentity(ctx); err != nil {
			slog.WarnContext(ctx, "keycloak close failed", "err", err)
		} else if n > 0 {
			slog.InfoContext(ctx, "deleted keycloak accounts", "count", n)
		}
		if r, o, err := s.Sweep(ctx); err != nil {
			slog.WarnContext(ctx, "privacy sweep failed", "err", err)
		} else if r+o > 0 {
			slog.InfoContext(ctx, "privacy sweep", "reminded", r, "overdue", o)
		}
		select {
		case <-ctx.Done():
			return nil
		case <-t.C:
		}
	}
}

// HashEmail chuẩn hoá (chữ thường, bỏ khoảng trắng) rồi băm SHA-256; rỗng nếu không có email.
func HashEmail(email string) string {
	e := strings.ToLower(strings.TrimSpace(email))
	if e == "" {
		return ""
	}
	sum := sha256.Sum256([]byte(e))
	return hex.EncodeToString(sum[:])
}
