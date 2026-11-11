// Package messaging đổi domain event sang protobuf (outbox) và tiêu thụ event từ Kafka.
package messaging

import (
	"fmt"

	"google.golang.org/protobuf/proto"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	cartv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/cart/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

const (
	Source        = "cart-service"
	AggregateType = "cart" // Debezium route → topic ecommerce.cart.events
)

func ToOutbox(e domain.Event) (outbox.Message, error) {
	var p proto.Message
	switch ev := e.(type) {
	case domain.CartItemAdded:
		p = &cartv1.CartItemAdded{CartId: ev.CartID, CustomerId: ev.CustomerID, ProductId: ev.ProductID, Quantity: ev.Quantity}
	case domain.CartItemRemoved:
		p = &cartv1.CartItemRemoved{CartId: ev.CartID, CustomerId: ev.CustomerID, ProductId: ev.ProductID}
	case domain.CartCleared:
		p = &cartv1.CartCleared{CartId: ev.CartID, CustomerId: ev.CustomerID, ProductIds: ev.ProductIDs}
	default:
		return outbox.Message{}, fmt.Errorf("unknown event %T", e)
	}
	return outbox.Message{AggregateType: AggregateType, AggregateID: e.AggregateID(), Payload: p}, nil
}
