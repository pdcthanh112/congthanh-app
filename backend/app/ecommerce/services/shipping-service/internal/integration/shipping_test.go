// Package integration_test: vòng đời vận đơn trên Postgres thật, kiểm cả outbox.
package integration_test

import (
	"context"
	"testing"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db/dbtest"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/adapter/carrier"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/migrations"
)

func TestShipmentLifecycle(t *testing.T) {
	pool := dbtest.New(t, "shipping", migrations.FS)
	svc := app.NewService(postgres.New(pool), postgres.UUIDv7{}, carrier.Internal{})
	ctx := context.Background()

	q, err := svc.Quote(ctx, domain.Address{CountryCode: "VN", RegionCode: "HCM"}, domain.Parcel{WeightGrams: 1200, DeclaredValue: 200000, Currency: "VND"})
	if err != nil || len(q) != 2 || q[0].Fee != 24000 {
		t.Fatalf("seed quotes: %+v %v", q, err)
	}

	addr := &domain.Address{RecipientName: "An", Line1: "1 Lê Lợi", RegionCode: "hcm", CountryCode: "vn"}
	for range 2 { // event giao lại không tạo trùng
		if err := svc.OnOrderPlaced(ctx, "o1", "c1", addr); err != nil {
			t.Fatal(err)
		}
	}
	sh, err := svc.ForOrder(ctx, "o1", "c1", false)
	if err != nil || sh.Status != domain.StatusPending || sh.Address.CountryCode != "VN" || len(sh.Events) != 1 {
		t.Fatalf("%+v %v", sh, err)
	}
	if _, err := svc.ForOrder(ctx, "o1", "someone-else", false); apperr.KindOf(err) != apperr.KindNotFound {
		t.Fatal("other customers must not see the shipment")
	}

	sh, err = svc.CreateLabel(ctx, sh.ID, "internal", "standard")
	if err != nil || sh.TrackingNumber == nil || sh.Status != domain.StatusLabelCreated {
		t.Fatalf("%+v %v", sh, err)
	}
	for _, st := range []domain.Status{domain.StatusPickedUp, domain.StatusInTransit, domain.StatusOutForDelivery, domain.StatusDelivered} {
		if _, err := svc.UpdateStatus(ctx, sh.ID, st, "", "HCM hub"); err != nil {
			t.Fatalf("-> %s: %v", st, err)
		}
	}
	if _, err := svc.UpdateStatus(ctx, sh.ID, domain.StatusCancelled, "", ""); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatal("delivered shipment cannot be cancelled")
	}
	tr, err := svc.Track(ctx, *sh.TrackingNumber)
	if err != nil || tr.Address != nil || tr.CustomerID != "" || len(tr.Events) != 6 {
		t.Fatalf("track: %+v %v", tr, err)
	}

	// Đơn bị huỷ trước khi lấy hàng → huỷ vận đơn; huỷ lần nữa vẫn ổn.
	_ = svc.OnOrderPlaced(ctx, "o2", "c1", nil)
	for range 2 {
		if err := svc.OnOrderCancelled(ctx, "o2"); err != nil {
			t.Fatal(err)
		}
	}
	if s2, _ := svc.ForOrder(ctx, "o2", "", true); s2.Status != domain.StatusCancelled {
		t.Fatalf("%+v", s2)
	}
	if err := svc.OnOrderCancelled(ctx, "unknown"); err != nil {
		t.Fatal(err)
	}

	var outbox int
	_ = pool.QueryRow(ctx, `SELECT count(*) FROM outbox WHERE aggregatetype = 'shipping'`).Scan(&outbox)
	// 2 ShipmentCreated + 5 đổi trạng thái của o1 + 1 huỷ o2.
	if outbox != 8 {
		t.Fatalf("outbox rows = %d", outbox)
	}
}
