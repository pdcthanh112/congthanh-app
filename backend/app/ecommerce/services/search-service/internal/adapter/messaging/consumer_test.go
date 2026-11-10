package messaging

import (
	"context"
	"testing"
	"time"

	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/timestamppb"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

type rec struct {
	applied []domain.Product
	removed []string
}

func (r *rec) Apply(_ context.Context, p domain.Product, _ time.Time) error {
	r.applied = append(r.applied, p)
	return nil
}
func (r *rec) Remove(_ context.Context, id string, _ time.Time) error {
	r.removed = append(r.removed, id)
	return nil
}

func TestHandler(t *testing.T) {
	r := &rec{}
	h := Handler(r)
	up, _ := anypb.New(&productv1.ProductUpserted{ProductId: "p1", Name: "Áo", Price: &commonv1.Money{CurrencyCode: "USD", Units: 19, Nanos: 990_000_000}, Status: "ACTIVE"})
	del, _ := anypb.New(&productv1.ProductDeleted{ProductId: "p2"})
	now := timestamppb.Now()
	for _, e := range []*commonv1.EventEnvelope{
		{Type: upsertedType, Payload: up, OccurredAt: now},
		{Type: deletedType, Payload: del, OccurredAt: now},
		{Type: "ecommerce.other.v1.Ignored"},
	} {
		if err := h(context.Background(), nil, e); err != nil {
			t.Fatal(err)
		}
	}
	if len(r.applied) != 1 || r.applied[0].Price != 19.99 || len(r.removed) != 1 || r.removed[0] != "p2" {
		t.Fatalf("%+v", r)
	}
}
