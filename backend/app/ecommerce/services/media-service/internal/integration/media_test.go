// Package integration_test: repository Postgres thật + storage giả.
package integration_test

import (
	"context"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db/dbtest"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/migrations"
)

type store map[string]app.ObjectInfo

func (s store) PresignPut(context.Context, string, string, int64, time.Duration) (string, map[string]string, error) {
	return "http://s3/x", nil, nil
}
func (s store) Stat(_ context.Context, k string) (app.ObjectInfo, bool, error) {
	i, ok := s[k]
	return i, ok, nil
}
func (s store) Remove(_ context.Context, k string) error { delete(s, k); return nil }
func (s store) PublicURL(k string) string                { return "https://cdn/" + k }

func TestMediaRepository(t *testing.T) {
	st := store{}
	svc := app.NewService(postgres.New(dbtest.New(t, "media", migrations.FS)), st, postgres.UUIDv7{}, app.Options{})
	ctx := context.Background()
	up, err := svc.RequestUpload(ctx, domain.UploadRequest{OwnerID: "u1", Purpose: domain.PurposeReview, FileName: "a.jpg", ContentType: "image/jpeg", SizeBytes: 50})
	if err != nil {
		t.Fatal(err)
	}
	st[up.Media.ObjectKey] = app.ObjectInfo{Size: 50, ContentType: "image/jpeg"}
	if _, err := svc.Complete(ctx, up.Media.ID, "u1", false); err != nil {
		t.Fatal(err)
	}
	list, err := svc.ListMine(ctx, "u1", 10)
	if err != nil || len(list) != 1 || list[0].URL == "" || list[0].CompletedAt == nil {
		t.Fatalf("%+v %v", list, err)
	}
	if n, err := svc.CleanupPending(ctx); err != nil || n != 0 {
		t.Fatalf("ready media must not be cleaned: %d %v", n, err)
	}
	if err := svc.Delete(ctx, up.Media.ID, "u1", false); err != nil {
		t.Fatal(err)
	}
}
