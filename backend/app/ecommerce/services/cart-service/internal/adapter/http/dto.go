package http

import (
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

// CartDTO / CartItemDTO / CartVm / CartItemVm giữ đúng tên field JSON của bản Java.
type CartItemDTO struct {
	ID        int64     `json:"id"`
	Cart      int64     `json:"cart"`
	Product   string    `json:"product"`
	Quantity  int32     `json:"quantity"`
	CreatedAt time.Time `json:"createdAt"`
}

type CartDTO struct {
	ID        int64         `json:"id"`
	Name      string        `json:"name"`
	Customer  string        `json:"customer"`
	CreatedAt time.Time     `json:"createdAt"`
	UpdatedAt time.Time     `json:"updatedAt"`
	Status    domain.Status `json:"status"`
	CartItems []CartItemDTO `json:"cartItems"`
	IsDefault bool          `json:"isDefault"`
	CountItem int           `json:"countItem"`
}

type CartItemVm struct {
	ID        int64     `json:"id"`
	ProductID string    `json:"productId"`
	Quantity  int32     `json:"quantity"`
	CreatedAt time.Time `json:"createdAt"`
}

type CartVm struct {
	ID         int64         `json:"id"`
	Name       string        `json:"name"`
	CustomerID string        `json:"customerId"`
	CreatedAt  time.Time     `json:"createdAt"`
	UpdatedAt  time.Time     `json:"updatedAt"`
	Status     domain.Status `json:"status"`
	CartItems  []CartItemVm  `json:"cartItems"`
	IsDefault  bool          `json:"isDefault"`
}

func toItemDTO(it domain.Item) CartItemDTO {
	return CartItemDTO{ID: it.ID, Cart: it.CartID, Product: it.ProductID, Quantity: it.Quantity, CreatedAt: it.CreatedAt}
}

func toDTO(c domain.Cart) CartDTO {
	d := CartDTO{ID: c.ID, Name: c.Name, Customer: c.CustomerID, CreatedAt: c.CreatedAt, UpdatedAt: c.UpdatedAt,
		Status: c.Status, IsDefault: c.IsDefault, CountItem: c.CountItem(), CartItems: make([]CartItemDTO, len(c.Items))}
	for i, it := range c.Items {
		d.CartItems[i] = toItemDTO(it)
	}
	return d
}

func toVm(c domain.Cart) CartVm {
	v := CartVm{ID: c.ID, Name: c.Name, CustomerID: c.CustomerID, CreatedAt: c.CreatedAt, UpdatedAt: c.UpdatedAt,
		Status: c.Status, IsDefault: c.IsDefault, CartItems: make([]CartItemVm, len(c.Items))}
	for i, it := range c.Items {
		v.CartItems[i] = CartItemVm{ID: it.ID, ProductID: it.ProductID, Quantity: it.Quantity, CreatedAt: it.CreatedAt}
	}
	return v
}
