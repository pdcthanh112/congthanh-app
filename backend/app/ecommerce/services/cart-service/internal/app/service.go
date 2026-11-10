package app

import (
	"context"
	"strings"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

type Options struct {
	MaxItemQuantity  int32
	MaxItemsPerCart  int
	ValidateProducts bool
}

type Service struct {
	read     Reader
	uow      UnitOfWork
	products ProductCatalog
	stock    StockChecker
	policy   domain.Policy
	validate bool
}

func NewService(read Reader, uow UnitOfWork, products ProductCatalog, stock StockChecker, opt Options) *Service {
	if opt.MaxItemQuantity <= 0 {
		opt.MaxItemQuantity = 99
	}
	if opt.MaxItemsPerCart <= 0 {
		opt.MaxItemsPerCart = 100
	}
	return &Service{read: read, uow: uow, products: products, stock: stock,
		policy:   domain.Policy{MaxItemQuantity: opt.MaxItemQuantity, MaxItemsPerCart: opt.MaxItemsPerCart},
		validate: opt.ValidateProducts && products != nil}
}

func (s *Service) Get(ctx context.Context, id int64) (domain.Cart, error) { return s.read.Get(ctx, id) }

func (s *Service) ListByCustomer(ctx context.Context, customerID string, activeOnly bool) ([]domain.Cart, error) {
	return s.read.ListByCustomer(ctx, customerID, activeOnly)
}

func (s *Service) GetDefault(ctx context.Context, customerID string) (domain.Cart, error) {
	return s.read.GetDefault(ctx, customerID)
}

type CreateInput struct {
	Name       string `json:"name"`
	CustomerID string `json:"customerId"`
	IsDefault  bool   `json:"isDefault"`
}

// Create tạo giỏ; giỏ đầu tiên của khách tự động là giỏ mặc định.
func (s *Service) Create(ctx context.Context, in CreateInput) (domain.Cart, error) {
	c, err := domain.NewCart(in.Name, in.CustomerID, in.IsDefault)
	if err != nil {
		return c, err
	}
	err = s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		if err := tx.LockCustomer(ctx, c.CustomerID); err != nil {
			return err
		}
		has, err := tx.HasDefault(ctx, c.CustomerID)
		if err != nil {
			return err
		}
		if !has {
			c.IsDefault = true
		} else if c.IsDefault {
			if err := tx.UnsetDefault(ctx, c.CustomerID, 0); err != nil {
				return err
			}
		}
		c, err = tx.InsertCart(ctx, c)
		return err
	})
	return c, err
}

type UpdateInput struct {
	Name      *string        `json:"name"`
	IsDefault *bool          `json:"isDefault"`
	Status    *domain.Status `json:"status"`
}

func (s *Service) Update(ctx context.Context, id int64, in UpdateInput) (domain.Cart, error) {
	var out domain.Cart
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		c, err := tx.LockCart(ctx, id)
		if err != nil {
			return err
		}
		if err := tx.LockCustomer(ctx, c.CustomerID); err != nil {
			return err
		}
		if in.Name != nil {
			c.Rename(*in.Name)
		}
		if in.Status != nil {
			if err := c.SetStatus(*in.Status); err != nil {
				return err
			}
		}
		switch {
		case in.IsDefault != nil && *in.IsDefault:
			if err := c.MakeDefault(); err != nil {
				return err
			}
			if err := tx.UnsetDefault(ctx, c.CustomerID, c.ID); err != nil {
				return err
			}
		case in.IsDefault != nil:
			c.IsDefault = false
		}
		out, err = tx.SaveCart(ctx, c)
		return err
	})
	if err != nil {
		return out, err
	}
	return s.read.Get(ctx, id)
}

func (s *Service) Delete(ctx context.Context, id int64) error {
	return s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		c, err := tx.LockCart(ctx, id)
		if err != nil {
			return err
		}
		removed, err := tx.DeleteItems(ctx, id, nil)
		if err != nil {
			return err
		}
		if len(removed) > 0 {
			if err := tx.Publish(ctx, domain.CartCleared{CartID: id, CustomerID: c.CustomerID, ProductIDs: removed}); err != nil {
				return err
			}
		}
		return tx.DeleteCart(ctx, id)
	})
}

// AddItem thêm sản phẩm; đã có thì cộng dồn số lượng.
func (s *Service) AddItem(ctx context.Context, cartID int64, productID string, qty int32) (domain.Item, error) {
	productID = strings.TrimSpace(productID)
	if productID == "" {
		return domain.Item{}, domain.Invalid("productId is required")
	}
	if qty < 1 {
		return domain.Item{}, domain.Invalid("quantity must be >= 1")
	}
	if err := s.checkProducts(ctx, productID); err != nil {
		return domain.Item{}, err
	}
	var it domain.Item
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		c, err := tx.LockCart(ctx, cartID)
		if err != nil {
			return err
		}
		if err := c.EnsureActive(); err != nil {
			return err
		}
		count, current, err := tx.ItemStats(ctx, cartID, productID)
		if err != nil {
			return err
		}
		if err := s.policy.CheckAdd(count, current, qty); err != nil {
			return err
		}
		if it, err = tx.AddItem(ctx, cartID, productID, qty); err != nil {
			return err
		}
		return tx.Publish(ctx, domain.CartItemAdded{CartID: cartID, CustomerID: c.CustomerID, ProductID: productID, Quantity: qty})
	})
	return it, err
}

func (s *Service) UpdateItem(ctx context.Context, cartID, itemID int64, qty int32) (domain.Item, error) {
	if err := s.policy.CheckQuantity(qty); err != nil {
		return domain.Item{}, err
	}
	var it domain.Item
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		var err error
		it, err = tx.SetItemQuantity(ctx, cartID, itemID, qty)
		return err
	})
	return it, err
}

func (s *Service) DeleteItem(ctx context.Context, cartID, itemID int64) error {
	return s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		c, err := tx.LockCart(ctx, cartID)
		if err != nil {
			return err
		}
		it, err := tx.DeleteItem(ctx, cartID, itemID)
		if err != nil {
			return err
		}
		return tx.Publish(ctx, domain.CartItemRemoved{CartID: cartID, CustomerID: c.CustomerID, ProductID: it.ProductID})
	})
}

// Clear xoá item (tất cả, hoặc theo productIDs — sau checkout).
func (s *Service) Clear(ctx context.Context, cartID int64, productIDs []string) (int, error) {
	var n int
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		c, err := tx.LockCart(ctx, cartID)
		if err != nil {
			return err
		}
		removed, err := tx.DeleteItems(ctx, cartID, productIDs)
		if err != nil || len(removed) == 0 {
			return err
		}
		n = len(removed)
		return tx.Publish(ctx, domain.CartCleared{CartID: cartID, CustomerID: c.CustomerID, ProductIDs: removed})
	})
	return n, err
}

// ClearOrdered bỏ khỏi giỏ mặc định các sản phẩm vừa đặt (event OrderPlaced).
func (s *Service) ClearOrdered(ctx context.Context, customerID string, productIDs []string) (int, error) {
	c, err := s.read.GetDefault(ctx, customerID)
	if domain.KindOf(err) == domain.KindNotFound || len(productIDs) == 0 {
		return 0, nil
	}
	if err != nil {
		return 0, err
	}
	return s.Clear(ctx, c.ID, productIDs)
}

// ItemDetail lấy item kèm thông tin sản phẩm.
func (s *Service) ItemDetail(ctx context.Context, cartID, itemID int64) (domain.ItemDetail, error) {
	it, err := s.read.Item(ctx, cartID, itemID)
	if err != nil {
		return domain.ItemDetail{}, err
	}
	d := domain.ItemDetail{ID: it.ID, Quantity: it.Quantity, Product: domain.ProductDetail{ID: it.ProductID}}
	if s.products == nil {
		return d, nil
	}
	if d.Product, err = s.products.Detail(ctx, it.ProductID); err != nil {
		return domain.ItemDetail{}, err
	}
	return d, nil
}

func (s *Service) checkProducts(ctx context.Context, ids ...string) error {
	if !s.validate {
		return nil
	}
	invalid, err := s.products.Invalid(ctx, ids)
	if err != nil {
		return err
	}
	for _, id := range ids {
		if msg, bad := invalid[id]; bad {
			return domain.Invalid("%s", msg)
		}
	}
	return nil
}

// Validate kiểm tra từng item còn đủ hàng và còn bán trước khi checkout.
func (s *Service) Validate(ctx context.Context, cartID int64) (domain.Validation, error) {
	c, err := s.read.Get(ctx, cartID)
	if err != nil || len(c.Items) == 0 {
		return domain.Evaluate(c, nil, nil), err
	}
	var avail map[string]int32
	if s.stock != nil {
		if avail, err = s.stock.Available(ctx, c.Items); err != nil {
			return domain.Validation{}, err
		}
	}
	var invalid map[string]string
	if s.validate {
		ids := make([]string, len(c.Items))
		for i, it := range c.Items {
			ids[i] = it.ProductID
		}
		if invalid, err = s.products.Invalid(ctx, ids); err != nil {
			return domain.Validation{}, err
		}
	}
	return domain.Evaluate(c, avail, invalid), nil
}
