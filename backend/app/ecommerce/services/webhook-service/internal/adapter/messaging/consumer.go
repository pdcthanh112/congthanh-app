// Package messaging đổi sự kiện nội bộ (Protobuf trên Kafka) thành sự kiện webhook công khai (JSON).
package messaging

import (
	"context"
	"encoding/json"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"google.golang.org/protobuf/encoding/protojson"
	"google.golang.org/protobuf/proto"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	shippingv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/shipping/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

var Topics = []string{"ecom.order.events", "ecom.product.events", "ecom.shipping.events"}

func name(m proto.Message) string { return string(m.ProtoReflect().Descriptor().FullName()) }

// publicEvent: tên message nội bộ → (loại sự kiện công khai, message rỗng để giải mã).
var publicEvent = map[string]struct {
	event string
	newFn func() proto.Message
}{
	name(&orderv1.OrderPlaced{}):              {domain.EventOrderCreated, func() proto.Message { return &orderv1.OrderPlaced{} }},
	name(&orderv1.OrderConfirmed{}):           {domain.EventOrderConfirmed, func() proto.Message { return &orderv1.OrderConfirmed{} }},
	name(&orderv1.OrderCancelled{}):           {domain.EventOrderCancelled, func() proto.Message { return &orderv1.OrderCancelled{} }},
	name(&productv1.ProductUpserted{}):        {domain.EventProductUpdated, func() proto.Message { return &productv1.ProductUpserted{} }},
	name(&productv1.ProductDeleted{}):         {domain.EventProductDeleted, func() proto.Message { return &productv1.ProductDeleted{} }},
	name(&shippingv1.ShipmentStatusChanged{}): {domain.EventShipmentStatusChanged, func() proto.Message { return &shippingv1.ShipmentStatusChanged{} }},
}

type FanOut interface {
	FanOutTx(ctx context.Context, tx pgx.Tx, eventID, event string, at time.Time, data json.RawMessage) error
}

// Payload JSON giữ tên trường camelCase của Protobuf và bỏ trường nội bộ (reservation, workflow id).
var marshal = protojson.MarshalOptions{EmitUnpopulated: false}

func Handler(f FanOut) func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
		pe, ok := publicEvent[env.GetType()]
		if !ok {
			return nil
		}
		msg := pe.newFn()
		if err := env.GetPayload().UnmarshalTo(msg); err != nil {
			slog.WarnContext(ctx, "bad event payload", "type", env.GetType(), "err", err)
			return nil
		}
		scrub(msg)
		data, err := marshal.Marshal(msg)
		if err != nil {
			return err
		}
		at := time.Now()
		if env.GetOccurredAt() != nil {
			at = env.GetOccurredAt().AsTime()
		}
		return f.FanOutTx(ctx, tx, env.GetId(), pe.event, at, data)
	}
}

// scrub bỏ trường chỉ có nghĩa nội bộ trước khi gửi ra ngoài.
func scrub(m proto.Message) {
	switch v := m.(type) {
	case *orderv1.OrderPlaced:
		v.CheckoutWorkflowId = ""
	case *orderv1.OrderCancelled:
		v.ReservationId = ""
	}
}
