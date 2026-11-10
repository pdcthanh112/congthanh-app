// Package app chứa use case của cart-service và các port (interface) mà adapter hiện thực.
package app

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

// Reader đọc dữ liệu ngoài transaction. Cart trả về luôn kèm Items.
type Reader interface {
	Get(ctx context.Context, id int64) (domain.Cart, error)
	ListByCustomer(ctx context.Context, customerID string, activeOnly bool) ([]domain.Cart, error)
	GetDefault(ctx context.Context, customerID string) (domain.Cart, error)
	Item(ctx context.Context, cartID, itemID int64) (domain.Item, error)
}

type UnitOfWork interface {
	Do(ctx context.Context, fn func(ctx context.Context, tx Tx) error) error
}

// Tx là các thao tác ghi trong một transaction; Publish ghi event vào outbox cùng transaction.
type Tx interface {
	// LockCustomer tuần tự hoá thao tác đổi giỏ mặc định của một khách.
	LockCustomer(ctx context.Context, customerID string) error
	HasDefault(ctx context.Context, customerID string) (bool, error)
	UnsetDefault(ctx context.Context, customerID string, exceptID int64) error
	InsertCart(ctx context.Context, c domain.Cart) (domain.Cart, error)
	LockCart(ctx context.Context, id int64) (domain.Cart, error) // không kèm Items
	SaveCart(ctx context.Context, c domain.Cart) (domain.Cart, error)
	DeleteCart(ctx context.Context, id int64) error
	// ItemStats: số sản phẩm khác nhau trong giỏ và số lượng hiện có của productID.
	ItemStats(ctx context.Context, cartID int64, productID string) (count int, current int32, err error)
	AddItem(ctx context.Context, cartID int64, productID string, qty int32) (domain.Item, error)
	SetItemQuantity(ctx context.Context, cartID, itemID int64, qty int32) (domain.Item, error)
	DeleteItem(ctx context.Context, cartID, itemID int64) (domain.Item, error)
	// DeleteItems xoá theo product (rỗng = tất cả), trả các product đã xoá.
	DeleteItems(ctx context.Context, cartID int64, productIDs []string) ([]string, error)
	Publish(ctx context.Context, events ...domain.Event) error
}

// ProductCatalog là product-service (gRPC). Có thể nil.
type ProductCatalog interface {
	// Invalid trả map product_id → lý do với các sản phẩm không bán được.
	Invalid(ctx context.Context, productIDs []string) (map[string]string, error)
	Detail(ctx context.Context, productID string) (domain.ProductDetail, error)
}

// StockChecker là inventory-service (gRPC). Có thể nil.
type StockChecker interface {
	Available(ctx context.Context, items []domain.Item) (map[string]int32, error)
}
