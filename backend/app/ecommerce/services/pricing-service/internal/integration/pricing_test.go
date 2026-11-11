// Package integration_test chạy use case giá trên Postgres thật (bỏ qua khi thiếu TEST_DATABASE_URL).
package integration_test

import (
	"context"
	"testing"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db/dbtest"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/migrations"
)

func ptr[T any](v T) *T { return &v }

func TestPricingFlow(t *testing.T) {
	pool := dbtest.New(t, "pricing", migrations.FS)
	repo := postgres.New(pool)
	now := time.Now().UTC().Truncate(time.Second)
	clock := func() time.Time { return now }
	svc := app.NewService(repo, nil, postgres.UUIDv7{}, clock)
	ctx := context.Background()
	outbox := func() int {
		var n int
		if err := pool.QueryRow(ctx, `SELECT count(*) FROM outbox`).Scan(&n); err != nil {
			t.Fatal(err)
		}
		return n
	}

	// Giá khởi tạo từ catalog, rồi admin đổi giá.
	if err := repo.SeedFromCatalog(ctx, "p1", "USD", 200_000); err != nil {
		t.Fatal(err)
	}
	if err := repo.SeedFromCatalog(ctx, "p1", "USD", 999_999); err != nil { // không ghi đè
		t.Fatal(err)
	}
	n, err := svc.UpsertPrices(ctx, "default", []domain.Price{{ProductID: "p1", Amount: 180_000}, {ProductID: "p2", Amount: 50_000}}, "admin")
	if err != nil || n != 2 {
		t.Fatalf("upsert: %d %v", n, err)
	}
	if n, _ := svc.UpsertPrices(ctx, "default", []domain.Price{{ProductID: "p2", Amount: 50_000}}, "admin"); n != 0 {
		t.Fatalf("unchanged upsert should be a no-op, changed=%d", n)
	}
	if got := outbox(); got != 3 { // IMPORTED p1, MANUAL p1, MANUAL p2
		t.Fatalf("outbox after upserts: %d", got)
	}

	ps, err := svc.Prices(ctx, []string{"p1", "p2", "nope"}, app.Context{})
	if err != nil || len(ps) != 2 || ps["p1"].Amount != 180_000 || *ps["p1"].Lowest30d != 180_000 {
		t.Fatalf("prices: %+v %v", ps, err)
	}

	// Bảng giá sỉ cho nhóm WHOLESALE.
	if _, err := svc.CreatePriceList(ctx, domain.PriceList{Code: "Wholesale", Name: "Wholesale", Currency: "usd", CustomerGroup: "wholesale", Priority: 10, Active: true}); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.CreatePriceList(ctx, domain.PriceList{Code: "wholesale", Name: "x", Currency: "USD", Active: true}); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("duplicate list: %v", err)
	}
	if _, err := svc.UpsertPrices(ctx, "wholesale", []domain.Price{{ProductID: "p1", Amount: 150_000}}, "admin"); err != nil {
		t.Fatal(err)
	}
	ws, _ := svc.Prices(ctx, []string{"p1"}, app.Context{Groups: []string{"Wholesale"}})
	if w := ws["p1"]; w.Amount != 150_000 || w.PriceList != "wholesale" || w.CompareAt != 180_000 || w.DiscountPercent != 16 {
		t.Fatalf("wholesale: %+v", w)
	}
	if pub, _ := svc.Prices(ctx, []string{"p1"}, app.Context{}); pub["p1"].Amount != 180_000 {
		t.Fatalf("public must not see wholesale: %+v", pub["p1"])
	}

	// Flash sale giảm 20% cho p1, p2 trong 2 giờ, bắt đầu sau 1 giờ.
	ss, err := svc.CreateSchedules(ctx, app.NewSchedule{ProductIDs: []string{"p1", "p2"}, Kind: domain.KindFlash, PercentOff: ptr(20.0),
		StartsAt: now.Add(time.Hour), EndsAt: ptr(now.Add(3 * time.Hour))}, "admin")
	if err != nil || len(ss) != 2 || ss[0].Amount != 144_000 || ss[1].Amount != 40_000 {
		t.Fatalf("schedules: %+v %v", ss, err)
	}
	if _, err := svc.CreateSchedules(ctx, app.NewSchedule{ProductIDs: []string{"p1"}, Kind: domain.KindFlash, Amount: ptr(int64(1)),
		StartsAt: now, EndsAt: ptr(now.Add(100 * time.Hour))}, "admin"); apperr.KindOf(err) != apperr.KindInvalid {
		t.Fatalf("flash > 72h must be rejected: %v", err)
	}
	preview, _ := svc.Prices(ctx, []string{"p1"}, app.Context{At: now.Add(90 * time.Minute)})
	if p := preview["p1"]; p.Amount != 144_000 || p.SaleKind != domain.KindFlash || p.DiscountPercent != 20 || p.SaleEndsAt == nil {
		t.Fatalf("preview during sale: %+v", p)
	}

	// Scheduler: bắt đầu → ACTIVE + event; kết thúc → ENDED + event.
	now = now.Add(61 * time.Minute)
	if err := svc.Tick(ctx); err != nil {
		t.Fatal(err)
	}
	active, _ := svc.Schedules(ctx, app.ScheduleFilter{Status: domain.Active})
	if len(active) != 2 || outbox() != 4+2 {
		t.Fatalf("after start: active=%d outbox=%d", len(active), outbox())
	}
	q, err := svc.QuoteLines(ctx, []app.Line{{LineID: "l1", ProductID: "p1", Quantity: 2}, {LineID: "l2", ProductID: "ghost", Quantity: 1}}, app.Context{})
	if err != nil || q.Subtotal != 288_000 || len(q.Unpriced) != 1 || q.Lines[0].CompareAt != 180_000 {
		t.Fatalf("quote: %+v %v", q, err)
	}
	if _, err := svc.CancelSchedule(ctx, ss[1].ID); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.CancelSchedule(ctx, ss[1].ID); apperr.KindOf(err) != apperr.KindConflict {
		t.Fatalf("double cancel: %v", err)
	}
	now = now.Add(3 * time.Hour)
	if err := svc.Tick(ctx); err != nil {
		t.Fatal(err)
	}
	ended, _ := svc.Schedules(ctx, app.ScheduleFilter{Status: domain.Ended})
	if len(ended) != 1 || outbox() != 6+2 { // cancel p2 + end p1
		t.Fatalf("after end: ended=%d outbox=%d", len(ended), outbox())
	}
	after, _ := svc.Prices(ctx, []string{"p1"}, app.Context{})
	if p := after["p1"]; p.Amount != 180_000 || *p.Lowest30d != 144_000 || p.DiscountPercent != 0 {
		t.Fatalf("after sale: %+v lowest=%d", p, *p.Lowest30d)
	}
	hist, _ := svc.History(ctx, "p1", 10)
	if len(hist) != 5 || hist[0].Reason != postgres.ReasonScheduleEnded {
		t.Fatalf("history: %+v", hist)
	}

	// Xoá sản phẩm: bỏ giá.
	if err := repo.RemoveProduct(ctx, "p2"); err != nil {
		t.Fatal(err)
	}
	if gone, _ := svc.Prices(ctx, []string{"p2"}, app.Context{}); len(gone) != 0 {
		t.Fatalf("removed product still priced: %+v", gone)
	}
	if err := svc.UpdatePriceList(ctx, domain.PriceList{Code: "default", Name: "x", Currency: "USD", Active: false}); apperr.KindOf(err) != apperr.KindInvalid {
		t.Fatalf("deactivate default: %v", err)
	}
}
