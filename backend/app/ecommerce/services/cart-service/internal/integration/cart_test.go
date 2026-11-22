package integration_test

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/anypb"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/testkit"
	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/repository/postgres"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/migrations"
)

type fakeProducts struct {
	inactive map[string]bool
	down     bool
}

func (f *fakeProducts) Invalid(_ context.Context, ids []string) (map[string]string, error) {
	if f.down {
		return nil, domain.Unavailable("dependent service unavailable")
	}
	out := map[string]string{}
	for _, id := range ids {
		if f.inactive[id] {
			out[id] = "product is not available for sale"
		}
	}
	return out, nil
}

func (f *fakeProducts) Detail(_ context.Context, id string) (domain.ProductDetail, error) {
	return domain.ProductDetail{ID: id, Name: "Name " + id, Slug: "slug"}, nil
}

type fakeStock map[string]int32

func (f fakeStock) Available(_ context.Context, items []domain.Item) (map[string]int32, error) {
	out := map[string]int32{}
	for _, it := range items {
		out[it.ProductID] = f[it.ProductID]
	}
	return out, nil
}

func setup(t *testing.T, p *fakeProducts, s fakeStock) (*app.Service, *pgxpool.Pool) {
	t.Helper()
	url := testkit.PostgresURL(t)
	ctx := context.Background()
	schema := fmt.Sprintf("cart_test_%d", time.Now().UnixNano())
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
	return app.NewService(repo, repo, repo, p, s, app.Options{MaxItemQuantity: 10, MaxItemsPerCart: 3, ValidateProducts: true}), pool
}

func kind(err error) domain.Kind { return domain.KindOf(err) }

func TestCartLifecycle(t *testing.T) {
	p := &fakeProducts{inactive: map[string]bool{"dead": true}}
	svc, pool := setup(t, p, fakeStock{"a": 5, "b": 1})
	ctx := context.Background()

	first, err := svc.Create(ctx, app.CreateInput{Name: "Giỏ 1", CustomerID: "u1"})
	if err != nil || !first.IsDefault {
		t.Fatalf("first cart must be default: %+v %v", first, err)
	}
	second, err := svc.Create(ctx, app.CreateInput{Name: "Giỏ 2", CustomerID: "u1", IsDefault: true})
	if err != nil || !second.IsDefault {
		t.Fatal("second cart should become default")
	}
	if d, _ := svc.GetDefault(ctx, "u1"); d.ID != second.ID {
		t.Fatal("default cart was not switched")
	}
	if upd, err := svc.Update(ctx, first.ID, app.UpdateInput{IsDefault: ptr(true)}); err != nil || !upd.IsDefault {
		t.Fatalf("make default: %+v %v", upd, err)
	}
	if d, _ := svc.GetDefault(ctx, "u1"); d.ID != first.ID {
		t.Fatal("default cart was not switched back")
	}

	if _, err := svc.AddItem(ctx, first.ID, "a", 2); err != nil {
		t.Fatal(err)
	}
	it, err := svc.AddItem(ctx, first.ID, "a", 3)
	if err != nil || it.Quantity != 5 {
		t.Fatalf("merge quantity: %+v %v", it, err)
	}
	if _, err := svc.AddItem(ctx, first.ID, "a", 6); kind(err) != domain.KindInvalid {
		t.Fatalf("expected max quantity error, got %v", err)
	}
	if _, err := svc.AddItem(ctx, first.ID, "dead", 1); kind(err) != domain.KindInvalid {
		t.Fatalf("expected inactive product error, got %v", err)
	}
	p.down = true
	if _, err := svc.AddItem(ctx, first.ID, "b", 1); kind(err) != domain.KindUnavailable {
		t.Fatalf("expected unavailable when product-service down, got %v", err)
	}
	p.down = false
	if _, err := svc.AddItem(ctx, first.ID, "b", 2); err != nil {
		t.Fatal(err)
	}

	v, err := svc.Validate(ctx, first.ID)
	if err != nil {
		t.Fatal(err)
	}
	if v.Valid || len(v.Items) != 2 || !v.Items[0].Valid || v.Items[1].Valid {
		t.Fatalf("validation = %+v", v)
	}

	c, _ := svc.Get(ctx, first.ID)
	d, err := svc.ItemDetail(ctx, first.ID, c.Items[0].ID)
	if err != nil || d.Product.Name != "Name a" {
		t.Fatalf("item detail = %+v %v", d, err)
	}
	if err := svc.DeleteItem(ctx, second.ID, c.Items[0].ID); kind(err) != domain.KindNotFound {
		t.Fatal("item must belong to the cart in the URL")
	}
	n, err := svc.Clear(ctx, first.ID, []string{"b"})
	if err != nil || n != 1 {
		t.Fatalf("clear = %d %v", n, err)
	}
	if err := svc.Delete(ctx, first.ID); err != nil {
		t.Fatal(err)
	}
	if _, err := svc.Get(ctx, first.ID); kind(err) != domain.KindNotFound {
		t.Fatal("cart should be deleted")
	}

	var added, cleared int
	if err := pool.QueryRow(ctx, `SELECT count(*) FILTER (WHERE type = 'ecommerce.cart.v1.CartItemAdded'),
		count(*) FILTER (WHERE type = 'ecommerce.cart.v1.CartCleared') FROM outbox WHERE aggregatetype = 'cart'`).Scan(&added, &cleared); err != nil {
		t.Fatal(err)
	}
	if added != 3 || cleared != 2 {
		t.Fatalf("outbox added=%d cleared=%d, want 3 and 2", added, cleared)
	}
}

func TestOrderConfirmedClearsDefaultCart(t *testing.T) {
	svc, _ := setup(t, &fakeProducts{}, fakeStock{})
	ctx := context.Background()
	c, err := svc.Create(ctx, app.CreateInput{Name: "Giỏ", CustomerID: "u9"})
	if err != nil {
		t.Fatal(err)
	}
	for _, p := range []string{"a", "b", "c"} {
		if _, err := svc.AddItem(ctx, c.ID, p, 1); err != nil {
			t.Fatal(err)
		}
	}
	h := messaging.OrderEventsHandler(svc)
	send := func(typ string, m proto.Message) {
		t.Helper()
		payload, _ := anypb.New(m)
		if err := h(ctx, nil, &commonv1.EventEnvelope{Type: typ, Payload: payload}); err != nil {
			t.Fatal(err)
		}
	}
	items := func() int {
		got, _ := svc.Get(ctx, c.ID)
		return len(got.Items)
	}

	send("ecommerce.order.v1.OrderPlaced", &orderv1.OrderPlaced{OrderId: "o1", CustomerId: "u9", Lines: []*orderv1.OrderLine{{ProductId: "a"}, {ProductId: "c"}}})
	if items() != 3 {
		t.Fatalf("OrderPlaced must not touch the cart, items=%d", items())
	}
	send("ecommerce.order.v1.OrderConfirmed", &orderv1.OrderConfirmed{OrderId: "o1"})
	got, _ := svc.Get(ctx, c.ID)
	if len(got.Items) != 1 || got.Items[0].ProductID != "b" {
		t.Fatalf("items after confirm = %+v", got.Items)
	}

	send("ecommerce.order.v1.OrderConfirmed", &orderv1.OrderConfirmed{OrderId: "o1"})

	send("ecommerce.order.v1.OrderPlaced", &orderv1.OrderPlaced{OrderId: "o2", CustomerId: "u9", Lines: []*orderv1.OrderLine{{ProductId: "b"}}})
	send("ecommerce.order.v1.OrderCancelled", &orderv1.OrderCancelled{OrderId: "o2", Reason: "payment failed"})
	send("ecommerce.order.v1.OrderConfirmed", &orderv1.OrderConfirmed{OrderId: "o2"})
	if items() != 1 {
		t.Fatalf("cancelled order must keep cart, items=%d", items())
	}

	send("ecommerce.order.v1.OrderPlaced", &orderv1.OrderPlaced{OrderId: "o3", CustomerId: "nobody", Lines: []*orderv1.OrderLine{{ProductId: "a"}}})
	send("ecommerce.order.v1.OrderConfirmed", &orderv1.OrderConfirmed{OrderId: "o3"})
}

func TestProcessedEventsKeyedByConsumer(t *testing.T) {
	_, pool := setup(t, &fakeProducts{}, fakeStock{})
	ctx := context.Background()
	for _, g := range []string{"cart-service", "cart-analytics"} {
		tag, err := pool.Exec(ctx, `INSERT INTO processed_events (event_id, consumer) VALUES ('e1', $1) ON CONFLICT DO NOTHING`, g)
		if err != nil || tag.RowsAffected() != 1 {
			t.Fatalf("consumer %s: rows=%d err=%v", g, tag.RowsAffected(), err)
		}
	}
}

func ptr[T any](v T) *T { return &v }
