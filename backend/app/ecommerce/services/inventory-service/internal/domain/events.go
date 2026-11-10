package domain

// Event là sự kiện nghiệp vụ phát ra sau khi trạng thái thay đổi. Adapter messaging đổi
// sang protobuf (api/proto/ecommerce/inventory/v1/inventory_events.proto) và ghi vào outbox.
type Event interface{ AggregateID() string }

type InventoryReserved struct {
	ReservationID string
	CustomerID    string
	Items         []ReservationItem
	ExpiresAtUnix int64
}

type InventoryReservationFailed struct {
	ReservationID string
	Reason        string
}

type InventoryReleased struct {
	ReservationID string
	Items         []ReservationItem
	Reason        string
	Expired       bool
}

type InventoryCommitted struct {
	ReservationID string
	OrderID       string
	Items         []ReservationItem
}

type StockLevelChanged struct {
	Stock  Stock
	Reason string
}

type LowStockDetected struct {
	ProductID    string
	Available    int32
	ReorderPoint int32
}

func (e InventoryReserved) AggregateID() string          { return e.ReservationID }
func (e InventoryReservationFailed) AggregateID() string { return e.ReservationID }
func (e InventoryReleased) AggregateID() string          { return e.ReservationID }
func (e InventoryCommitted) AggregateID() string         { return e.ReservationID }
func (e StockLevelChanged) AggregateID() string          { return e.Stock.ProductID }
func (e LowStockDetected) AggregateID() string           { return e.ProductID }
