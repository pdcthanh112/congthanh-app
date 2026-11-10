package domain

import (
	"cmp"
	"fmt"
	"slices"
	"time"
)

type ReservationStatus string

const (
	StatusReserved  ReservationStatus = "RESERVED"
	StatusReleased  ReservationStatus = "RELEASED"
	StatusExpired   ReservationStatus = "EXPIRED"
	StatusCommitted ReservationStatus = "COMMITTED"
)

type ReservationType string

func ParseReservationType(s string) ReservationType {
	switch s {
	case "CART", "QUOTE", "FLASH_SALE":
		return ReservationType(s)
	}
	return "ORDER"
}

// Reservation là lượt giữ chỗ hàng (saga đặt hàng, flash sale...). ID do bên gọi cấp
// nên mọi thao tác đều idempotent theo ID.
type Reservation struct {
	ID         string
	CustomerID string
	OrderID    string
	Type       ReservationType
	Status     ReservationStatus
	Reason     string
	ExpiresAt  time.Time
}

// ReservationItem: phần hàng giữ tại một dòng tồn kho cụ thể.
type ReservationItem struct {
	StockID     int64
	ProductID   string
	WarehouseID string
	Quantity    int32
}

// Allocation là kết quả chia số lượng cần giữ cho các kho.
type Allocation struct {
	Line  ReserveLine
	Items []ReservationItem
}

func (a Allocation) Complete() bool { return a.Line.Reserved >= a.Line.Requested }

// Allocate chia it.Quantity cho các dòng tồn kho, ưu tiên kho còn nhiều hàng nhất.
// Hàm thuần: không đổi stocks.
func Allocate(it Item, stocks []Stock) Allocation {
	cands := slices.Clone(stocks)
	slices.SortStableFunc(cands, func(a, b Stock) int { return cmp.Compare(b.Sellable(), a.Sellable()) })
	al := Allocation{Line: ReserveLine{ProductID: it.ProductID, Requested: it.Quantity}}
	need := it.Quantity
	var total int32
	for _, s := range cands {
		total += s.Sellable()
		if need == 0 || s.Sellable() == 0 {
			continue
		}
		take := min(need, s.Sellable())
		al.Items = append(al.Items, ReservationItem{StockID: s.ID, ProductID: it.ProductID, WarehouseID: s.WarehouseID, Quantity: take})
		need -= take
	}
	al.Line.Reserved = it.Quantity - need
	if len(al.Items) > 0 {
		al.Line.WarehouseID = al.Items[0].WarehouseID
	}
	if need > 0 {
		al.Line.Error = fmt.Sprintf("only %d available", total)
	}
	return al
}

type ReserveLine struct {
	ProductID   string `json:"productId"`
	Requested   int32  `json:"requestedQuantity"`
	Reserved    int32  `json:"reservedQuantity"`
	WarehouseID string `json:"warehouseId"`
	Error       string `json:"errorMessage,omitempty"`
}

type ReserveResult struct {
	Success    bool          `json:"success"`
	ID         string        `json:"reservationId"`
	Lines      []ReserveLine `json:"results"`
	Message    string        `json:"message"`
	ReservedAt time.Time     `json:"reservedAt"`
	ExpiresAt  time.Time     `json:"expiresAt"`
}

type ReleaseLine struct {
	ProductID string `json:"productId"`
	Released  int32  `json:"releasedQuantity"`
}

type ReleaseResult struct {
	Success    bool          `json:"success"`
	ID         string        `json:"reservationId"`
	Lines      []ReleaseLine `json:"results"`
	Message    string        `json:"message"`
	ReleasedAt time.Time     `json:"releasedAt"`
}

type CommitLine struct {
	ProductID    string `json:"productId"`
	Committed    int32  `json:"committedQuantity"`
	NewAvailable int32  `json:"newAvailableQuantity"`
}

type CommitResult struct {
	Success     bool         `json:"success"`
	ID          string       `json:"reservationId"`
	Lines       []CommitLine `json:"results"`
	Message     string       `json:"message"`
	CommittedAt time.Time    `json:"committedAt"`
}
