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

// reservationID khớp CheckoutContract.reservationId: saga dùng id này khi giữ/chốt/trả hàng của đơn.
func reservationID(claimID string) string { return "checkout-" + claimID }

func startSale(t *testing.T, svc *app.Service, in app.FlashSaleInput) string {
	t.Helper()
	claims.reset()
	in.SaleID = fmt.Sprintf("s%d", time.Now().UnixNano())
	if in.EndsAt.IsZero() {
		in.EndsAt = time.Now().Add(time.Hour)
	}
	if r, err := svc.StartFlashSale(context.Background(), in); err != nil || !r.Success {
		t.Fatalf("start: %+v %v", r, err)
	}
	return in.SaleID
}

// 200 người tranh 20 suất qua Redis Lua: đúng 20 claim, giới hạn mỗi khách được tôn trọng. Mỗi claim
// thành đơn qua saga (Reserve → Commit theo id đơn = id claim) mà không giữ thêm hàng trong Postgres;
// cuối sale số bán được chốt vào Postgres, phần dư trả về kho.
func TestFlashSaleNeverOversells(t *testing.T) {
	if os.Getenv("TEST_REDIS_URL") == "" {
		t.Skip("TEST_REDIS_URL not set")
	}
	svc, _ := setup(t)
	ctx := context.Background()
	seed(t, svc, "hot", "WH-DEFAULT", 100)
	sale := startSale(t, svc, app.FlashSaleInput{ProductID: "hot", Quantity: 20, PerCustomerLimit: 2})
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
	// Còn claim chưa thành đơn thì chưa đóng sale được.
	if _, err := svc.EndFlashSale(ctx, sale, "hot"); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("end with open claims: %v, want conflict", err)
	}
	for _, c := range claims.all {
		item := []domain.Item{{ProductID: "hot", Quantity: c.Quantity}}
		id := reservationID(c.ClaimID)
		if r, err := svc.Reserve(ctx, app.ReserveInput{ID: id, CustomerID: c.CustomerID, Items: item}); err != nil || !r.Success {
			t.Fatalf("reserve claim: %+v %v", r, err)
		}
		if r, err := svc.Commit(ctx, id, c.ClaimID, item); err != nil || !r.Success {
			t.Fatalf("commit claim: %+v %v", r, err)
		}
	}
	if got := available(t, svc, "hot"); got != 80 {
		t.Fatalf("flash orders must not touch regular stock: available = %d, want 80", got)
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

// Vòng đời claim: sai khách/sai số lượng bị từ chối; saga bù trừ thì suất quay lại; claim không đặt đơn
// trước hạn bị thu hồi, còn claim đã có đơn (CONFIRMED) thì không.
func TestFlashSaleClaimLifecycle(t *testing.T) {
	if os.Getenv("TEST_REDIS_URL") == "" {
		t.Skip("TEST_REDIS_URL not set")
	}
	svc, _ := setupWith(t, app.Options{FlashClaimTTL: time.Second})
	ctx := context.Background()
	seed(t, svc, "hot", "WH-DEFAULT", 10)
	sale := startSale(t, svc, app.FlashSaleInput{ProductID: "hot", Quantity: 3, PerCustomerLimit: 1})
	item := []domain.Item{{ProductID: "hot", Quantity: 1}}

	a, err := svc.ClaimFlashSale(ctx, sale, "hot", "alice", 1)
	if err != nil || a.CheckoutSessionID != a.ClaimID || a.Remaining != 2 {
		t.Fatalf("claim: %+v %v", a, err)
	}
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: reservationID(a.ClaimID), CustomerID: "mallory", Items: item}); r.Success {
		t.Fatal("another customer must not use the claim")
	}
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: reservationID(a.ClaimID), CustomerID: "alice", Items: []domain.Item{{ProductID: "hot", Quantity: 2}}}); r.Success {
		t.Fatal("quantity must match the claim")
	}
	if r, err := svc.Reserve(ctx, app.ReserveInput{ID: reservationID(a.ClaimID), CustomerID: "alice", Items: item}); err != nil || !r.Success {
		t.Fatalf("reserve: %+v %v", r, err)
	}

	// bob giành rồi thanh toán lỗi: saga trả hàng, suất quay lại, bob được giành lại.
	b, _ := svc.ClaimFlashSale(ctx, sale, "hot", "bob", 1)
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "bob", 1); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("second claim over limit: %v", err)
	}
	for range 2 { // bù trừ gọi lại vẫn thành công
		if r, err := svc.Release(ctx, reservationID(b.ClaimID), nil, "payment failed", false); err != nil || !r.Success {
			t.Fatalf("release: %+v %v", r, err)
		}
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "bob", 1); err != nil {
		t.Fatalf("claim after release: %v", err)
	}

	// carol giành nhưng không đặt đơn. Sau hạn: claim của bob và carol bị thu hồi, của alice thì không.
	c, _ := svc.ClaimFlashSale(ctx, sale, "hot", "carol", 1)
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "dave", 1); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("sold out expected: %v", err)
	}
	time.Sleep(1100 * time.Millisecond)
	if n, err := svc.SweepFlashClaims(ctx, 100); err != nil || n != 2 {
		t.Fatalf("sweep = %d %v, want 2", n, err)
	}
	if r, _ := svc.Reserve(ctx, app.ReserveInput{ID: reservationID(c.ClaimID), CustomerID: "carol", Items: item}); r.Success {
		t.Fatal("expired claim must not become an order")
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "dave", 1); err != nil {
		t.Fatalf("dave should get the returned slot: %v", err)
	}
	if r, err := svc.Commit(ctx, reservationID(a.ClaimID), a.ClaimID, item); err != nil || !r.Success {
		t.Fatalf("commit: %+v %v", r, err)
	}
	if r, _ := svc.Release(ctx, reservationID(a.ClaimID), nil, "late", false); r.Success {
		t.Fatal("a committed claim must not be released")
	}
	time.Sleep(1100 * time.Millisecond) // claim của dave hết hạn
	sum, err := svc.EndFlashSale(ctx, sale, "hot")
	if err != nil || sum.Sold != 1 || sum.Returned != 2 {
		t.Fatalf("end: %+v %v", sum, err)
	}
	if got := available(t, svc, "hot"); got != 9 {
		t.Fatalf("available after sale = %d, want 9", got)
	}
	// Đơn thường (id không phải claim) vẫn giữ hàng trong Postgres như cũ.
	if r, err := svc.Reserve(ctx, app.ReserveInput{ID: "checkout-0198c5a0-0000-7000-8000-000000000001", CustomerID: "erin", Items: item}); err != nil || !r.Success {
		t.Fatalf("regular reserve: %+v %v", r, err)
	}
	if got := available(t, svc, "hot"); got != 8 {
		t.Fatalf("available after regular order = %d, want 8", got)
	}
}

// Phòng chờ: phải xếp hàng mới được giành, vào theo thứ tự đến và theo tốc độ admitPerSecond.
func TestFlashSaleWaitingRoom(t *testing.T) {
	if os.Getenv("TEST_REDIS_URL") == "" {
		t.Skip("TEST_REDIS_URL not set")
	}
	svc, _ := setup(t)
	ctx := context.Background()
	seed(t, svc, "hot", "WH-DEFAULT", 100)
	sale := startSale(t, svc, app.FlashSaleInput{
		ProductID: "hot", Quantity: 50, PerCustomerLimit: 1,
		OpensAt: time.Now().Add(700 * time.Millisecond), AdmitPerSecond: 4, AdmitBurst: 2,
	})
	for i := range 10 {
		q, err := svc.FlashSaleQueue(ctx, sale, "hot", fmt.Sprintf("c%d", i), true)
		if err != nil || q.Position != int64(i+1) || q.CanClaim || !q.WaitingRoom {
			t.Fatalf("join %d: %+v %v", i, q, err)
		}
	}
	if q, _ := svc.FlashSaleQueue(ctx, sale, "hot", "c3", true); q.Position != 4 || q.QueueLength != 10 {
		t.Fatalf("joining twice must keep the position: %+v", q)
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "c0", 1); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("claim before opening: %v", err)
	}
	time.Sleep(750 * time.Millisecond)
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "outsider", 1); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("claim without queueing: %v", err)
	}
	if q, _ := svc.FlashSaleQueue(ctx, sale, "hot", "outsider", false); q.Position != 0 || q.CanClaim {
		t.Fatalf("status without joining: %+v", q)
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "c0", 1); err != nil {
		t.Fatalf("head of the queue should claim: %v", err)
	}
	q, _ := svc.FlashSaleQueue(ctx, sale, "hot", "c9", false)
	if q.CanClaim || q.Ahead == 0 || q.EstimatedWaitSeconds == 0 || q.RetryAfterSeconds == 0 {
		t.Fatalf("tail should still wait: %+v", q)
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "c9", 1); domain.KindOf(err) != domain.KindConflict {
		t.Fatalf("tail claimed too early: %v", err)
	}
	time.Sleep(2100 * time.Millisecond) // 2 + 4/s * ~2.1s >= 10
	if q, _ := svc.FlashSaleQueue(ctx, sale, "hot", "c9", false); !q.CanClaim || q.Ahead != 0 {
		t.Fatalf("tail should be admitted: %+v", q)
	}
	if _, err := svc.ClaimFlashSale(ctx, sale, "hot", "c9", 1); err != nil {
		t.Fatalf("tail claim: %v", err)
	}
}
