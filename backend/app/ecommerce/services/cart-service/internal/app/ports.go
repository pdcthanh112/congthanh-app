package app

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

type Reader interface {
	Get(ctx context.Context, id int64) (domain.Cart, error)
	ListByCustomer(ctx context.Context, customerID string, activeOnly bool) ([]domain.Cart, error)
	GetDefault(ctx context.Context, customerID string) (domain.Cart, error)
	Item(ctx context.Context, cartID, itemID int64) (domain.Item, error)
}

type PendingOrders interface {
	RememberOrder(ctx context.Context, orderID, customerID string, productIDs []string) error
	PendingOrder(ctx context.Context, orderID string) (customerID string, productIDs []string, found bool, err error)
	ForgetOrder(ctx context.Context, orderID string) error
}

type UnitOfWork interface {
	Do(ctx context.Context, fn func(ctx context.Context, tx Tx) error) error
}

type Tx interface {
	LockCustomer(ctx context.Context, customerID string) error
	HasDefault(ctx context.Context, customerID string) (bool, error)
	UnsetDefault(ctx context.Context, customerID string, exceptID int64) error
	InsertCart(ctx context.Context, c domain.Cart) (domain.Cart, error)
	LockCart(ctx context.Context, id int64) (domain.Cart, error)
	SaveCart(ctx context.Context, c domain.Cart) (domain.Cart, error)
	DeleteCart(ctx context.Context, id int64) error
	ItemStats(ctx context.Context, cartID int64, productID string) (count int, current int32, err error)
	AddItem(ctx context.Context, cartID int64, productID string, qty int32) (domain.Item, error)
	SetItemQuantity(ctx context.Context, cartID, itemID int64, qty int32) (domain.Item, error)
	DeleteItem(ctx context.Context, cartID, itemID int64) (domain.Item, error)
	DeleteItems(ctx context.Context, cartID int64, productIDs []string) ([]string, error)
	Publish(ctx context.Context, events ...domain.Event) error
}

type ProductCatalog interface {
	Invalid(ctx context.Context, productIDs []string) (map[string]string, error)
	Detail(ctx context.Context, productID string) (domain.ProductDetail, error)
}

type StockChecker interface {
	Available(ctx context.Context, items []domain.Item) (map[string]int32, error)
}
