package domain

import (
	"strings"
	"time"
)

type Status string

const (
	StatusActive   Status = "ACTIVE"
	StatusInactive Status = "INACTIVE"
)

type Item struct {
	ID        int64
	CartID    int64
	ProductID string
	Quantity  int32
	CreatedAt time.Time
	UpdatedAt time.Time
}

type Cart struct {
	ID         int64
	Name       string
	CustomerID string
	Status     Status
	IsDefault  bool
	CreatedAt  time.Time
	UpdatedAt  time.Time
	Items      []Item
}

func (c Cart) CountItem() int { return len(c.Items) }

// NewCart kiểm tra dữ liệu đầu vào của một giỏ mới.
func NewCart(name, customerID string, isDefault bool) (Cart, error) {
	name, customerID = strings.TrimSpace(name), strings.TrimSpace(customerID)
	if name == "" || customerID == "" {
		return Cart{}, Invalid("name and customerId are required")
	}
	return Cart{Name: name, CustomerID: customerID, Status: StatusActive, IsDefault: isDefault, Items: []Item{}}, nil
}

func (c Cart) EnsureActive() error {
	if c.Status != StatusActive {
		return Invalid("cart is not active")
	}
	return nil
}

// Rename đổi tên; tên rỗng bị bỏ qua.
func (c *Cart) Rename(name string) {
	if n := strings.TrimSpace(name); n != "" {
		c.Name = n
	}
}

// SetStatus: giỏ INACTIVE không thể là giỏ mặc định.
func (c *Cart) SetStatus(s Status) error {
	if s != StatusActive && s != StatusInactive {
		return Invalid("status must be ACTIVE or INACTIVE")
	}
	c.Status = s
	if s == StatusInactive {
		c.IsDefault = false
	}
	return nil
}

func (c *Cart) MakeDefault() error {
	if c.Status != StatusActive {
		return Invalid("only an active cart can be the default")
	}
	c.IsDefault = true
	return nil
}

// Policy là giới hạn nghiệp vụ của giỏ.
type Policy struct {
	MaxItemQuantity int32
	MaxItemsPerCart int
}

func (p Policy) CheckQuantity(q int32) error {
	if q < 1 {
		return Invalid("quantity must be >= 1")
	}
	if q > p.MaxItemQuantity {
		return Invalid("quantity per product cannot exceed %d", p.MaxItemQuantity)
	}
	return nil
}

// CheckAdd: count = số sản phẩm khác nhau trong giỏ, current = số lượng hiện có của sản phẩm (0 nếu chưa có).
func (p Policy) CheckAdd(count int, current, add int32) error {
	if add < 1 {
		return Invalid("quantity must be >= 1")
	}
	if current == 0 && count >= p.MaxItemsPerCart {
		return Invalid("cart cannot hold more than %d products", p.MaxItemsPerCart)
	}
	return p.CheckQuantity(current + add)
}
