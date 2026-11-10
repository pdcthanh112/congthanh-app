package domain

import (
	"strings"
	"time"
)

// Stock là tồn kho của một sản phẩm tại một kho. Bất biến: 0 <= Reserved <= Quantity.
type Stock struct {
	ID              int64     `json:"id"`
	ProductID       string    `json:"productId"`
	SKU             string    `json:"sku"`
	WarehouseID     string    `json:"warehouseId"`
	WarehouseName   string    `json:"warehouseName,omitempty"`
	Location        string    `json:"location,omitempty"`
	Quantity        int32     `json:"quantity"`
	Reserved        int32     `json:"reserved"`
	InTransit       int32     `json:"inTransit"`
	ReorderPoint    int32     `json:"reorderPoint"`
	ReorderQuantity int32     `json:"reorderQuantity"`
	Discontinued    bool      `json:"discontinued"`
	UpdatedAt       time.Time `json:"updatedAt"`
}

func (s Stock) Available() int32 { return s.Quantity - s.Reserved }

// Sellable là số có thể bán (kho ngừng kinh doanh tính là 0).
func (s Stock) Sellable() int32 {
	if s.Discontinued {
		return 0
	}
	return max(s.Available(), 0)
}

func (s *Stock) Reserve(q int32) error {
	if q <= 0 || q > s.Available() {
		return Conflict("cannot reserve %d of %s at %s (available %d)", q, s.ProductID, s.WarehouseID, s.Available())
	}
	s.Reserved += q
	return nil
}

func (s *Stock) Release(q int32) { s.Reserved = max(s.Reserved-q, 0) }

// Commit trừ hẳn committed khỏi tồn kho và bỏ giữ chỗ held (held >= committed).
func (s *Stock) Commit(committed, held int32) {
	s.Quantity -= committed
	s.Reserved = max(s.Reserved-held, 0)
}

// SetQuantity đặt tồn kho tuyệt đối; không được thấp hơn số đang giữ chỗ.
func (s *Stock) SetQuantity(q int32) error {
	if q < 0 {
		return Invalid("quantity must be >= 0")
	}
	if q < s.Reserved {
		return Conflict("quantity would drop below reserved (%d)", s.Reserved)
	}
	s.Quantity = q
	return nil
}

func (s *Stock) Adjust(change int32) error { return s.SetQuantity(s.Quantity + change) }

func (s Stock) BelowReorderPoint() bool { return s.ReorderPoint > 0 && s.Available() <= s.ReorderPoint }

type AdjustmentType string

var adjustmentTypes = map[string]bool{"RESTOCK": true, "DAMAGE": true, "LOSS": true, "FOUND": true, "RETURN": true, "CORRECTION": true}

func ParseAdjustmentType(s string) (AdjustmentType, error) {
	t := strings.ToUpper(strings.TrimSpace(s))
	if t == "" {
		return "CORRECTION", nil
	}
	if !adjustmentTypes[t] {
		return "", Invalid("unknown adjustment type %q", s)
	}
	return AdjustmentType(t), nil
}

// Movement là một dòng lịch sử thay đổi tồn kho.
type Movement struct {
	StockID     int64
	ProductID   string
	WarehouseID string
	Kind        string
	Change      int32
	ReferenceID string
	Reason      string
	Actor       string
}

type LevelStatus string

const (
	LevelInStock      LevelStatus = "IN_STOCK"
	LevelLowStock     LevelStatus = "LOW_STOCK"
	LevelOutOfStock   LevelStatus = "OUT_OF_STOCK"
	LevelBackorder    LevelStatus = "BACKORDER"
	LevelDiscontinued LevelStatus = "DISCONTINUED"
)

type Level struct {
	ProductID       string      `json:"productId"`
	Available       int32       `json:"availableQuantity"`
	Reserved        int32       `json:"reservedQuantity"`
	InTransit       int32       `json:"inTransitQuantity"`
	Total           int32       `json:"totalQuantity"`
	ReorderPoint    int32       `json:"reorderPoint"`
	ReorderQuantity int32       `json:"reorderQuantity"`
	Status          LevelStatus `json:"status"`
	LastUpdated     time.Time   `json:"lastUpdated"`
}

// ComputeLevel tổng hợp tồn kho của một sản phẩm qua mọi kho.
func ComputeLevel(productID string, stocks []Stock) Level {
	l := Level{ProductID: productID}
	if len(stocks) == 0 {
		l.Status = LevelOutOfStock
		return l
	}
	allDiscontinued := true
	for _, s := range stocks {
		l.Total += s.Quantity
		l.Reserved += s.Reserved
		l.InTransit += s.InTransit
		l.ReorderPoint += s.ReorderPoint
		l.ReorderQuantity += s.ReorderQuantity
		if !s.Discontinued {
			l.Available += s.Available()
			allDiscontinued = false
		}
		if s.UpdatedAt.After(l.LastUpdated) {
			l.LastUpdated = s.UpdatedAt
		}
	}
	switch {
	case allDiscontinued:
		l.Status = LevelDiscontinued
	case l.Available <= 0 && l.InTransit > 0:
		l.Status = LevelBackorder
	case l.Available <= 0:
		l.Status = LevelOutOfStock
	case l.Available <= l.ReorderPoint:
		l.Status = LevelLowStock
	default:
		l.Status = LevelInStock
	}
	return l
}

type Availability struct {
	ProductID string  `json:"productId"`
	Requested int32   `json:"requestedQuantity"`
	Available int32   `json:"availableQuantity"`
	Reserved  int32   `json:"reservedQuantity"`
	InTransit int32   `json:"inTransitQuantity"`
	Stocks    []Stock `json:"warehouseStocks"`
}

func (a Availability) IsAvailable() bool { return a.Available >= a.Requested }

func ComputeAvailability(it Item, stocks []Stock) Availability {
	a := Availability{ProductID: it.ProductID, Requested: it.Quantity, Stocks: stocks}
	for _, s := range stocks {
		a.Available += s.Sellable()
		a.Reserved += s.Reserved
		a.InTransit += s.InTransit
	}
	return a
}
