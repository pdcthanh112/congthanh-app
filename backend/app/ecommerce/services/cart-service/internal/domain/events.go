package domain

import "strconv"

// Event là domain event; adapter messaging đổi sang protobuf và ghi outbox.
type Event interface{ AggregateID() string }

type CartItemAdded struct {
	CartID     int64
	CustomerID string
	ProductID  string
	Quantity   int32
}

type CartItemRemoved struct {
	CartID     int64
	CustomerID string
	ProductID  string
}

type CartCleared struct {
	CartID     int64
	CustomerID string
	ProductIDs []string
}

func (e CartItemAdded) AggregateID() string   { return strconv.FormatInt(e.CartID, 10) }
func (e CartItemRemoved) AggregateID() string { return strconv.FormatInt(e.CartID, 10) }
func (e CartCleared) AggregateID() string     { return strconv.FormatInt(e.CartID, 10) }
