package app

import (
	"context"
	"strconv"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/domain"
)

type memRepo map[string]domain.Media

func (r memRepo) Create(_ context.Context, m domain.Media) error { r[m.ID] = m; return nil }
func (r memRepo) Get(_ context.Context, id string) (domain.Media, error) {
	m, ok := r[id]
	if !ok {
		return m, apperr.NotFound("media %s not found", id)
	}
	return m, nil
}
func (r memRepo) MarkReady(_ context.Context, id string, at time.Time) error {
	m := r[id]
	m.Status, m.CompletedAt = domain.StatusReady, &at
	r[id] = m
	return nil
}
func (r memRepo) Delete(_ context.Context, id string) error { delete(r, id); return nil }
func (r memRepo) ListByOwner(_ context.Context, o string, _ int) ([]domain.Media, error) {
	var out []domain.Media
	for _, m := range r {
		if m.OwnerID == o {
			out = append(out, m)
		}
	}
	return out, nil
}
func (r memRepo) StalePending(_ context.Context, before time.Time, _ int) ([]domain.Media, error) {
	var out []domain.Media
	for _, m := range r {
		if m.Status == domain.StatusPending && m.CreatedAt.Before(before) {
			out = append(out, m)
		}
	}
	return out, nil
}

type memStore struct{ objects map[string]ObjectInfo }

func (s *memStore) PresignPut(_ context.Context, key, ct string, _ int64, _ time.Duration) (string, map[string]string, error) {
	return "https://s3.local/" + key + "?sig=x", map[string]string{"Content-Type": ct}, nil
}
func (s *memStore) Stat(_ context.Context, key string) (ObjectInfo, bool, error) {
	i, ok := s.objects[key]
	return i, ok, nil
}
func (s *memStore) Remove(_ context.Context, key string) error { delete(s.objects, key); return nil }
func (s *memStore) PublicURL(key string) string                { return "https://cdn.local/" + key }

type seq struct{ n int }

func (s *seq) NewID() string { s.n++; return "m" + strconv.Itoa(s.n) }

func TestUploadLifecycle(t *testing.T) {
	repo, store := memRepo{}, &memStore{objects: map[string]ObjectInfo{}}
	svc := NewService(repo, store, &seq{}, Options{})
	ctx := context.Background()

	up, err := svc.RequestUpload(ctx, domain.UploadRequest{OwnerID: "u1", Purpose: domain.PurposeAvatar, FileName: "me.png", ContentType: "image/png", SizeBytes: 1234})
	if err != nil || up.UploadURL == "" || up.Media.Status != domain.StatusPending {
		t.Fatalf("%+v %v", up, err)
	}
	id := up.Media.ID
	if _, err := svc.Get(ctx, id); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatal("pending media must be hidden")
	}
	if _, err := svc.Complete(ctx, id, "u1", false); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("not uploaded yet: %v", err)
	}
	if _, err := svc.Complete(ctx, id, "u2", false); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatalf("other user: %v", err)
	}
	store.objects[up.Media.ObjectKey] = ObjectInfo{Size: 1234, ContentType: "image/png"}
	m, err := svc.Complete(ctx, id, "u1", false)
	if err != nil || m.Status != domain.StatusReady || m.URL != "https://cdn.local/"+up.Media.ObjectKey {
		t.Fatalf("%+v %v", m, err)
	}

	// Khai 1234 byte nhưng tải lên file khác dung lượng → bị xoá.
	up2, _ := svc.RequestUpload(ctx, domain.UploadRequest{OwnerID: "u1", Purpose: domain.PurposeAvatar, FileName: "x.png", ContentType: "image/png", SizeBytes: 10})
	store.objects[up2.Media.ObjectKey] = ObjectInfo{Size: 999999, ContentType: "image/png"}
	if _, err := svc.Complete(ctx, up2.Media.ID, "u1", false); apperr.KindOf(err) != apperr.KindInvalid {
		t.Fatalf("mismatch: %v", err)
	}
	if _, ok := store.objects[up2.Media.ObjectKey]; ok {
		t.Fatal("mismatched object must be removed")
	}

	up3, _ := svc.RequestUpload(ctx, domain.UploadRequest{OwnerID: "u1", Purpose: domain.PurposeAvatar, FileName: "y.png", ContentType: "image/png", SizeBytes: 10})
	svc.now = func() time.Time { return time.Now().Add(48 * time.Hour) }
	if n, err := svc.CleanupPending(ctx); err != nil || n != 1 {
		t.Fatalf("cleanup: %d %v", n, err)
	}
	if _, ok := repo[up3.Media.ID]; ok {
		t.Fatal("stale pending must be deleted")
	}
	if err := svc.Delete(ctx, id, "u2", true); err != nil {
		t.Fatal(err)
	}
}
