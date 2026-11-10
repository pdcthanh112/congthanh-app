// Package messaging đổi domain event sang protobuf (outbox) và tiêu thụ event từ Kafka.
package messaging

import (
	"fmt"

	"google.golang.org/protobuf/proto"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	invv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

const (
	Source        = "inventory-service"
	AggregateType = "inventory" // Debezium route → topic ecom.inventory.events
	TopicEvents   = "ecom.inventory.events"
)

func lines(items []domain.ReservationItem) []*invv1.ReservedLine {
	out := make([]*invv1.ReservedLine, len(items))
	for i, it := range items {
		out[i] = &invv1.ReservedLine{ProductId: it.ProductID, WarehouseId: it.WarehouseID, Quantity: it.Quantity}
	}
	return out
}

// ToOutbox đổi domain event thành message outbox.
func ToOutbox(e domain.Event) (outbox.Message, error) {
	var p proto.Message
	switch ev := e.(type) {
	case domain.InventoryReserved:
		p = &invv1.InventoryReserved{ReservationId: ev.ReservationID, CustomerId: ev.CustomerID, Lines: lines(ev.Items), ExpiresAt: ev.ExpiresAtUnix}
	case domain.InventoryReservationFailed:
		p = &invv1.InventoryReservationFailed{ReservationId: ev.ReservationID, Reason: ev.Reason}
	case domain.InventoryReleased:
		p = &invv1.InventoryReleased{ReservationId: ev.ReservationID, Lines: lines(ev.Items), Reason: ev.Reason, Expired: ev.Expired}
	case domain.InventoryCommitted:
		p = &invv1.InventoryCommitted{ReservationId: ev.ReservationID, OrderId: ev.OrderID, Lines: lines(ev.Items)}
	case domain.StockLevelChanged:
		p = &invv1.StockLevelChanged{ProductId: ev.Stock.ProductID, WarehouseId: ev.Stock.WarehouseID,
			Quantity: ev.Stock.Quantity, Available: ev.Stock.Available(), Reason: ev.Reason}
	case domain.LowStockDetected:
		p = &invv1.LowStockDetected{ProductId: ev.ProductID, Available: ev.Available, ReorderPoint: ev.ReorderPoint}
	default:
		return outbox.Message{}, fmt.Errorf("unknown domain event %T", e)
	}
	return outbox.Message{AggregateType: AggregateType, AggregateID: e.AggregateID(), Payload: p}, nil
}
