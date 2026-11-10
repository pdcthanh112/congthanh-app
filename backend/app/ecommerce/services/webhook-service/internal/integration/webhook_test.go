// Package integration_test: fan-out trong transaction, worker gửi, backoff, DEAD và retry trên Postgres thật.
package integration_test

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db/dbtest"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/delivery"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/migrations"
)

func TestWebhookDelivery(t *testing.T) {
	pool := dbtest.New(t, "webhook", migrations.FS)
	var calls, fail atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		calls.Add(1)
		if fail.Load() == 1 {
			w.WriteHeader(http.StatusInternalServerError)
			return
		}
		w.WriteHeader(http.StatusNoContent)
	}))
	defer srv.Close()

	repo := postgres.New(pool)
	svc := app.NewService(repo, delivery.New(5*time.Second, true), postgres.UUIDv7{}, true)
	ctx := context.Background()

	hook, err := svc.Create(ctx, domain.Webhook{Name: "ERP", URL: srv.URL, Events: []string{domain.EventOrderCreated, domain.EventOrderCreated}, Active: true})
	if err != nil || len(hook.Secret) < 20 || len(hook.Events) != 1 {
		t.Fatalf("%+v %v", hook, err)
	}
	if got, _ := svc.Get(ctx, hook.ID); got.Secret != "" {
		t.Fatal("secret must not be returned after create")
	}
	_, _ = svc.Create(ctx, domain.Webhook{Name: "Other", URL: srv.URL, Events: []string{domain.EventProductUpdated}, Active: true})

	// Fan-out hai lần cùng event id (Kafka giao lại) chỉ tạo một lượt giao.
	for range 2 {
		tx, _ := pool.Begin(ctx)
		if err := svc.FanOutTx(ctx, tx, "evt-1", domain.EventOrderCreated, time.Now(), json.RawMessage(`{"orderId":"o1"}`)); err != nil {
			t.Fatal(err)
		}
		_ = tx.Commit(ctx)
	}
	if n, err := svc.DeliverDue(ctx, 10); err != nil || n != 1 || calls.Load() != 1 {
		t.Fatalf("deliver: n=%d calls=%d %v", n, calls.Load(), err)
	}
	ds, _ := svc.Deliveries(ctx, hook.ID, 10)
	if len(ds) != 1 || ds[0].Status != domain.DeliverySucceeded || *ds[0].LastStatusCode != 204 {
		t.Fatalf("%+v", ds)
	}

	// Lỗi 500 → thử lại sau backoff, chưa đến hạn thì không gửi.
	fail.Store(1)
	d, _ := svc.Ping(ctx, hook.ID)
	if n, _ := svc.DeliverDue(ctx, 10); n != 1 {
		t.Fatal("ping must be delivered once")
	}
	if n, _ := svc.DeliverDue(ctx, 10); n != 0 {
		t.Fatal("failed delivery must wait for backoff")
	}
	got, _ := repo.GetDelivery(ctx, d.ID)
	if got.Status != domain.DeliveryPending || got.Attempts != 1 || got.NextAttemptAt.Before(time.Now().Add(20*time.Second)) {
		t.Fatalf("%+v", got)
	}
	// Retry tay: gửi ngay và thành công.
	fail.Store(0)
	if err := svc.Retry(ctx, d.ID); err != nil {
		t.Fatal(err)
	}
	if n, _ := svc.DeliverDue(ctx, 10); n != 1 {
		t.Fatal("retry must deliver")
	}
	if got, _ := repo.GetDelivery(ctx, d.ID); got.Status != domain.DeliverySucceeded {
		t.Fatalf("%+v", got)
	}
	if err := svc.Retry(ctx, d.ID); err == nil {
		t.Fatal("cannot retry a succeeded delivery")
	}

	// Webhook bị tắt: lượt giao chuyển DEAD.
	hook.Active = false
	if _, err := svc.Update(ctx, hook); err != nil {
		t.Fatal(err)
	}
	d2, _ := svc.Ping(ctx, hook.ID)
	_, _ = svc.DeliverDue(ctx, 10)
	if got, _ := repo.GetDelivery(ctx, d2.ID); got.Status != domain.DeliveryDead {
		t.Fatalf("%+v", got)
	}
}
