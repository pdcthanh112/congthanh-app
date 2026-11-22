package domain

import "strconv"

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
