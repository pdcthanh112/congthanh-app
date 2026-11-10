// Package grpcclient là driven adapter gọi product-service và inventory-service qua gRPC,
// hiện thực app.ProductCatalog và app.StockChecker. Circuit breaking / retry do Istio lo.
package grpcclient

import (
	"context"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	invv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

// toDomain đổi lỗi gRPC của service khác sang lỗi nghiệp vụ.
func toDomain(err error) error {
	switch status.Code(err) {
	case codes.NotFound:
		return domain.NotFound("product not found")
	case codes.InvalidArgument:
		return domain.Invalid("%s", status.Convert(err).Message())
	case codes.Unavailable, codes.DeadlineExceeded:
		return domain.Unavailable("dependent service unavailable")
	}
	return err
}

type Products struct {
	c productv1.ProductServiceClient
}

func NewProducts(cc grpc.ClientConnInterface) *Products {
	return &Products{productv1.NewProductServiceClient(cc)}
}

var (
	_ app.ProductCatalog = (*Products)(nil)
	_ app.StockChecker   = (*Inventory)(nil)
)

func (p *Products) Invalid(ctx context.Context, ids []string) (map[string]string, error) {
	resp, err := p.c.ValidateProducts(ctx, &productv1.ValidateProductRequest{ProductIds: ids, CheckActive: true})
	if err != nil {
		return nil, toDomain(err)
	}
	out := map[string]string{}
	for _, id := range resp.GetInvalidProducts() {
		out[id] = "invalid product: " + id
	}
	for _, e := range resp.GetErrors() {
		if e.GetErrorMessage() != "" {
			out[e.GetProductId()] = e.GetErrorMessage()
		}
	}
	return out, nil
}

func (p *Products) Detail(ctx context.Context, id string) (domain.ProductDetail, error) {
	r, err := p.c.GetProductById(ctx, &productv1.ProductRequest{ProductId: id})
	if err != nil {
		return domain.ProductDetail{}, toDomain(err)
	}
	return domain.ProductDetail{ID: r.GetId(), Name: r.GetName(), Slug: r.GetSlug(), SKU: r.GetSku(), Price: r.GetPrice()}, nil
}

type Inventory struct{ c invv1.InventoryServiceClient }

func NewInventory(cc grpc.ClientConnInterface) *Inventory {
	return &Inventory{invv1.NewInventoryServiceClient(cc)}
}

func (i *Inventory) Available(ctx context.Context, items []domain.Item) (map[string]int32, error) {
	req := &invv1.CheckAvailabilityRequest{}
	for _, it := range items {
		req.Items = append(req.Items, &invv1.InventoryItem{ProductId: it.ProductID, Quantity: it.Quantity})
	}
	resp, err := i.c.CheckAvailability(ctx, req)
	if err != nil {
		return nil, toDomain(err)
	}
	out := make(map[string]int32, len(resp.GetResults()))
	for _, r := range resp.GetResults() {
		out[r.GetProductId()] = r.GetAvailableQuantity()
	}
	return out, nil
}
