package integration_test

import (
	"context"
	"fmt"
	"os"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"google.golang.org/protobuf/proto"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

// Mọi thay đổi trạng thái đều sinh event trong outbox, cùng transaction.
func TestOutboxEventsAreWritten(t *testing.T) {
	svc, pool := setup(t)
	ctx := context.Background()
	seed(t, svc, "p1", "WH-DEFAULT", 3)
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: "o1", Items: []domain.Item{{ProductID: "p1", Quantity: 2}}}); !r.Success {
		t.Fatal("reserve failed")
	}
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: "o2", Items: []domain.Item{{ProductID: "p1", Quantity: 5}}}); r.Success {
		t.Fatal("reserve should fail")
	}
	rows, err := pool.Query(ctx, `SELECT aggregatetype, type, payload FROM outbox ORDER BY created_at, id`)
	if err != nil {
		t.Fatal(err)
	}
	defer rows.Close()
	var types []string
	for rows.Next() {
		var agg, typ string
		var payload []byte
		if err := rows.Scan(&agg, &typ, &payload); err != nil {
			t.Fatal(err)
		}
		env := &commonv1.EventEnvelope{}
		if err := proto.Unmarshal(payload, env); err != nil || env.GetType() != typ || agg != "inventory" {
			t.Fatalf("bad envelope %q %q: %v", agg, typ, err)
		}
		types = append(types, typ)
	}
	want := map[string]bool{"ecommerce.inventory.v1.StockLevelChanged": false, "ecommerce.inventory.v1.InventoryReserved": false, "ecommerce.inventory.v1.InventoryReservationFailed": false}
	for _, ty := range types {
		if _, ok := want[ty]; ok {
			want[ty] = true
		}
	}
	for ty, seen := range want {
		if !seen {
			t.Errorf("missing %s in outbox, got %v", ty, types)
		}
	}
}

// 200 người tranh 20 suất qua Redis Lua: đúng 20 claim, giới hạn mỗi khách được tôn trọng,
// cuối sale số bán được chốt vào Postgres, phần dư trả về kho.
func TestFlashSaleNeverOversells(t *testing.T) {
	if os.Getenv("TEST_REDIS_URL") == "" {
		t.Skip("TEST_REDIS_URL not set")
	}
	svc, _ := setup(t)
	ctx := context.Background()
	seed(t, svc, "hot", "WH-DEFAULT", 100)
	sale := fmt.Sprintf("s%d", time.Now().UnixNano())
	if r, err := svc.StartFlashSale(ctx, app.FlashSaleInput{SaleID: sale, ProductID: "hot", Quantity: 20, PerCustomerLimit: 2, EndsAt: time.Now().Add(time.Hour)}); err != nil || !r.Success {
		t.Fatalf("start: %+v %v", r, err)
	}
	if got := available(t, svc, "hot"); got != 80 {
		t.Fatalf("available during sale = %d, want 80", got)
	}
	var ok atomic.Int32
	var wg sync.WaitGroup
	for i := range 200 {
		wg.Add(1)
		go func() {
			defer wg.Done()
			cust := fmt.Sprintf("c%d", i%60) // 60 khách, mỗi người bấm nhiều lần
			if _, err := svc.ClaimFlashSale(ctx, sale, "hot", cust, 1); err == nil {
				ok.Add(1)
			} else if domain.KindOf(err) != domain.KindConflict {
				t.Error(err)
			}
		}()
	}
	wg.Wait()
	if ok.Load() != 20 {
		t.Fatalf("claims = %d, want 20", ok.Load())
	}
	for c, q := range claims.sent {
		if q > 2 {
			t.Fatalf("customer %s got %d > limit", c, q)
		}
	}
	sum, err := svc.EndFlashSale(ctx, sale, "hot")
	if err != nil || sum.Sold != 20 || sum.Returned != 0 {
		t.Fatalf("end: %+v %v", sum, err)
	}
	lv, err := svc.Levels(ctx, []string{"hot"}, "")
	if err != nil || lv[0].Total != 80 || lv[0].Reserved != 0 {
		t.Fatalf("after sale level = %+v %v", lv, err)
	}
}
