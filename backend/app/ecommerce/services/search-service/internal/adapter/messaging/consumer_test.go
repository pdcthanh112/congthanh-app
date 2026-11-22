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

func TestVariantEventIsNotIndexedSeparately(t *testing.T) {
	r := &rec{}
	up, _ := anypb.New(&productv1.ProductUpserted{ProductId: "v1", ParentId: "p1", Status: "ACTIVE"})
	if err := Handler(r)(context.Background(), nil, &commonv1.EventEnvelope{Type: upsertedType, Payload: up}); err != nil {
		t.Fatal(err)
	}
	if len(r.applied) != 0 || len(r.removed) != 1 || r.removed[0] != "v1" {
		t.Fatalf("%+v", r)
	}
}

func TestToProductFoldsVariantsAttributesAndOptions(t *testing.T) {
	n := 225.0
	vnd := func(u int64) *commonv1.Money { return &commonv1.Money{CurrencyCode: "VND", Units: u} }
	black := &productv1.OptionValue{OptionName: "Color", Value: "Black"}
	p := ToProduct(&productv1.ProductUpserted{
		ProductId: "p1", Name: "Iphone", BrandId: 7, CategoryIds: []string{"c1", "c2"}, CategoryId: "c1", HasVariant: true,
		Attributes: []*productv1.AttributeValue{
			{Code: "pages", Name: "Pages", Value: "225", Number: &n, Filterable: true},
			{Code: "author", Name: "Author", Value: "Paulo Coelho", Filterable: true, Searchable: true},
			{Code: "", Name: "Note", Value: "x", Filterable: true}, // không có code: không lọc được
		},
		Variants: []*productv1.VariantSummary{
			{ProductId: "v1", Sku: "A", Status: "ACTIVE", Price: vnd(25_000_000), Options: []*productv1.OptionValue{black, {OptionName: "Capacity", Value: "128 GB"}}},
			{ProductId: "v2", Sku: "B", Status: "ACTIVE", Price: vnd(35_000_000), Options: []*productv1.OptionValue{black, {OptionName: "Capacity", Value: "256 GB"}}},
			{ProductId: "v3", Sku: "C", Status: "INACTIVE", Price: vnd(1), Options: []*productv1.OptionValue{{OptionName: "Color", Value: "Pink"}}},
			{ProductId: "v4", Sku: "D", Status: "ACTIVE", Price: &commonv1.Money{CurrencyCode: "USD", Units: 999}},
		},
	})
	if p.BrandID != "7" || len(p.CategoryIDs) != 2 || p.Currency != "VND" || p.Price != 25_000_000 || p.PriceMax != 35_000_000 {
		t.Fatalf("price/brand: %+v", p)
	}
	if len(p.SKUs) != 3 || p.SearchText != "Paulo Coelho" || len(p.Attributes) != 2 || *p.Attributes[0].Number != 225 {
		t.Fatalf("skus/attrs: %+v", p)
	}
	// Black chỉ một lần; Pink thuộc variant ngừng bán nên không vào facet.
	if len(p.Options) != 3 || p.Options[0] != (domain.Option{Name: "Color", Value: "Black"}) {
		t.Fatalf("options: %+v", p.Options)
	}
}
