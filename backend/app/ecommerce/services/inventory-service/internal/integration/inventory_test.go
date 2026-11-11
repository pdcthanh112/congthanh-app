package integration_test

import (
	"context"
	"fmt"
	"os"
	"sync"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/migrations"
)

// Test tích hợp: cần Postgres thật. Chạy với
//
//	TEST_DATABASE_URL=postgres://postgres@localhost:5432/test?sslmode=disable go test ./...
func setup(t *testing.T) (*app.Service, *pgxpool.Pool) {
	t.Helper()
	return setupWith(t, app.Options{})
}

func setupWith(t *testing.T, opt app.Options) (*app.Service, *pgxpool.Pool) {
	t.Helper()
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}
	ctx := context.Background()
	schema := fmt.Sprintf("inv_test_%d", time.Now().UnixNano())
	pool, err := db.Connect(ctx, url, schema)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DROP SCHEMA "+schema+" CASCADE")
		pool.Close()
	})
	if err := db.Migrate(ctx, pool, migrations.FS, "goose_db_version"); err != nil {
		t.Fatal(err)
	}
	ids, _ := idgen.New(1)
	repo := postgres.New(pool, ids)
	opt.ReservationTTL, opt.DefaultWarehouse = 15*time.Minute, "WH-DEFAULT"
	return app.NewService(repo, repo, repo.Warehouses(), flashStore(t), claims, opt), pool
}

func seed(t *testing.T, svc *app.Service, product, wh string, qty int32) {
	t.Helper()
	if _, err := svc.UpsertStock(context.Background(), app.UpsertStockInput{ProductID: product, SKU: "SKU-" + product, WarehouseID: wh, Quantity: &qty}, "test"); err != nil {
		t.Fatal(err)
	}
}

func available(t *testing.T, svc *app.Service, product string) int32 {
	t.Helper()
	av, err := svc.CheckAvailability(context.Background(), []domain.Item{{ProductID: product, Quantity: 1}}, "")
	if err != nil {
		t.Fatal(err)
	}
	return av[0].Available
}

func TestReserveReleaseCommit(t *testing.T) {
	svc, _ := setup(t)
	ctx := context.Background()
	if _, err := svc.CreateWarehouse(ctx, app.WarehouseInput{ID: "WH-NJ", Name: "Newark, NJ"}); err != nil {
		t.Fatal(err)
	}
	seed(t, svc, "p1", "WH-DEFAULT", 5)
	seed(t, svc, "p1", "WH-NJ", 3)
	seed(t, svc, "p2", "WH-DEFAULT", 2)

	// Giữ 7 p1 → phải chia qua hai kho.
	res, err := svc.Reserve(ctx, app.ReserveInput{ID: "saga-1", Items: []domain.Item{{ProductID: "p1", Quantity: 4}, {ProductID: "p1", Quantity: 3}, {ProductID: "p2", Quantity: 1}}})
	if err != nil || !res.Success {
		t.Fatalf("reserve: %+v %v", res, err)
	}
	if got := available(t, svc, "p1"); got != 1 {
		t.Fatalf("p1 available = %d, want 1", got)
	}

	// Idempotent.
	again, err := svc.Reserve(ctx, app.ReserveInput{ID: "saga-1", Items: []domain.Item{{ProductID: "p1", Quantity: 7}}})
	if err != nil || !again.Success || available(t, svc, "p1") != 1 {
		t.Fatalf("idempotent reserve failed: %+v %v", again, err)
	}

	// Thiếu hàng → all-or-nothing, không giữ gì.
	fail, err := svc.Reserve(ctx, app.ReserveInput{ID: "saga-2", Items: []domain.Item{{ProductID: "p2", Quantity: 1}, {ProductID: "p1", Quantity: 2}}})
	if err != nil || fail.Success {
		t.Fatalf("expected failure: %+v %v", fail, err)
	}
	if available(t, svc, "p2") != 1 {
		t.Fatal("failed reservation must not hold p2")
	}

	// Release rồi release lại vẫn OK.
	rel, err := svc.Release(ctx, "saga-1", nil, "cancel", false)
	if err != nil || !rel.Success || available(t, svc, "p1") != 8 {
		t.Fatalf("release: %+v %v", rel, err)
	}
	if rel2, err := svc.Release(ctx, "saga-1", nil, "cancel", false); err != nil || !rel2.Success {
		t.Fatalf("second release: %+v %v", rel2, err)
	}

	// Commit trừ hẳn tồn kho.
	if r, err := svc.Reserve(ctx, app.ReserveInput{ID: "saga-3", Items: []domain.Item{{ProductID: "p1", Quantity: 6}}}); err != nil || !r.Success {
		t.Fatalf("reserve 3: %+v %v", r, err)
	}
	com, err := svc.Commit(ctx, "saga-3", "order-3", []domain.Item{{ProductID: "p1", Quantity: 5}}) // chốt 5, trả lại 1
	if err != nil || !com.Success {
		t.Fatalf("commit: %+v %v", com, err)
	}
	lv, err := svc.Levels(ctx, []string{"p1"}, "")
	if err != nil {
		t.Fatal(err)
	}
	if lv[0].Total != 3 || lv[0].Reserved != 0 || lv[0].Available != 3 {
		t.Fatalf("after commit level = %+v", lv[0])
	}
	if _, err := svc.Release(ctx, "saga-3", nil, "late", false); err != nil {
		t.Fatal(err)
	}
	if available(t, svc, "p1") != 3 {
		t.Fatal("release after commit must not change stock")
	}
}

func TestAdjustCannotGoBelowReserved(t *testing.T) {
	svc, _ := setup(t)
	ctx := context.Background()
	seed(t, svc, "p1", "WH-DEFAULT", 5)
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: "r", Items: []domain.Item{{ProductID: "p1", Quantity: 4}}}); !r.Success {
		t.Fatal("reserve failed")
	}
	res, err := svc.Adjust(ctx, []app.Adjustment{{ProductID: "p1", Change: -2, Type: "DAMAGE"}, {ProductID: "p9", Change: 10, Type: "RESTOCK"}}, "check", "", "u")
	if err != nil {
		t.Fatal(err)
	}
	if res.Success || res.Lines[0].Success || !res.Lines[1].Success || res.Lines[1].New != 10 {
		t.Fatalf("adjust result = %+v", res)
	}
}

func TestExpiredReservationsAreReleased(t *testing.T) {
	svc, pool := setup(t)
	ctx := context.Background()
	seed(t, svc, "p1", "WH-DEFAULT", 5)
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: "old", Items: []domain.Item{{ProductID: "p1", Quantity: 5}}}); !r.Success {
		t.Fatal("reserve failed")
	}
	if _, err := pool.Exec(ctx, `UPDATE reservations SET expires_at = now() - interval '1 minute'`); err != nil {
		t.Fatal(err)
	}
	n, err := svc.ReleaseExpired(ctx, 10)
	if err != nil || n != 1 || available(t, svc, "p1") != 5 {
		t.Fatalf("expired release n=%d err=%v", n, err)
	}
}

// Nhiều saga tranh nhau giữ hàng: không bao giờ bán vượt tồn kho.
func TestConcurrentReservationsNeverOversell(t *testing.T) {
	svc, _ := setup(t)
	ctx := context.Background()
	seed(t, svc, "hot", "WH-DEFAULT", 10)
	var wg sync.WaitGroup
	var mu sync.Mutex
	ok := 0
	for i := range 30 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			r, err := svc.Reserve(ctx, app.ReserveInput{ID: fmt.Sprintf("c-%d", i), Items: []domain.Item{{ProductID: "hot", Quantity: 1}}})
			if err != nil {
				t.Error(err)
				return
			}
			if r.Success {
				mu.Lock()
				ok++
				mu.Unlock()
			}
		}()
	}
	wg.Wait()
	if ok != 10 || available(t, svc, "hot") != 0 {
		t.Fatalf("successful reservations = %d, want 10", ok)
	}
}
