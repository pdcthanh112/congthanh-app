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
	fraudv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/fraud/v1"
	inventoryv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	paymentv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/payment/v1"
	pricingv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/pricing/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	shippingv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/shipping/v1"
	streamv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/stream/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

var Topics = []string{
	"ecommerce.order.events", "ecommerce.product.events", "ecommerce.shipping.events",
	"ecommerce.payment.events", "ecommerce.pricing.events", "ecommerce.inventory.events", "ecommerce.fraud.events",
	"ecommerce.stream.stock-cover",
}

func name(m proto.Message) string { return string(m.ProtoReflect().Descriptor().FullName()) }

var publicEvent = map[string]struct {
	event string
	newFn func() proto.Message
}{
	name(&orderv1.OrderPlaced{}):              {domain.EventOrderCreated, func() proto.Message { return &orderv1.OrderPlaced{} }},
	name(&orderv1.OrderConfirmed{}):           {domain.EventOrderConfirmed, func() proto.Message { return &orderv1.OrderConfirmed{} }},
	name(&orderv1.OrderCancelled{}):           {domain.EventOrderCancelled, func() proto.Message { return &orderv1.OrderCancelled{} }},
	name(&productv1.ProductUpserted{}):        {domain.EventProductUpdated, func() proto.Message { return &productv1.ProductUpserted{} }},
	name(&productv1.ProductDeleted{}):         {domain.EventProductDeleted, func() proto.Message { return &productv1.ProductDeleted{} }},
	name(&shippingv1.ShipmentCreated{}):       {domain.EventShipmentCreated, func() proto.Message { return &shippingv1.ShipmentCreated{} }},
	name(&shippingv1.ShipmentStatusChanged{}): {domain.EventShipmentStatusChanged, func() proto.Message { return &shippingv1.ShipmentStatusChanged{} }},
	name(&paymentv1.PaymentAuthorized{}):      {domain.EventPaymentAuthorized, func() proto.Message { return &paymentv1.PaymentAuthorized{} }},
	name(&paymentv1.PaymentFailed{}):          {domain.EventPaymentFailed, func() proto.Message { return &paymentv1.PaymentFailed{} }},
	name(&paymentv1.PaymentRefunded{}):        {domain.EventPaymentRefunded, func() proto.Message { return &paymentv1.PaymentRefunded{} }},
	name(&pricingv1.PriceChanged{}):           {domain.EventPriceChanged, func() proto.Message { return &pricingv1.PriceChanged{} }},
	name(&inventoryv1.LowStockDetected{}):     {domain.EventStockLow, func() proto.Message { return &inventoryv1.LowStockDetected{} }},
	name(&fraudv1.FraudReviewRequested{}):     {domain.EventCheckoutReviewRequested, func() proto.Message { return &fraudv1.FraudReviewRequested{} }},
	name(&fraudv1.FraudReviewDecided{}):       {domain.EventCheckoutReviewDecided, func() proto.Message { return &fraudv1.FraudReviewDecided{} }},
	name(&streamv1.StockCover{}):              {domain.EventStockoutRisk, func() proto.Message { return &streamv1.StockCover{} }},
}

func publish(m proto.Message) bool {
	if v, ok := m.(*streamv1.StockCover); ok {
		return v.GetAtRisk() && v.GetRiskChanged()
	}
	return true
}

type FanOut interface {
	FanOutTx(ctx context.Context, tx pgx.Tx, eventID, event string, at time.Time, data json.RawMessage) error
}

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
		if !publish(msg) {
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

func scrub(m proto.Message) {
	switch v := m.(type) {
	case *orderv1.OrderPlaced:
		v.CheckoutWorkflowId = ""
	case *orderv1.OrderCancelled:
		v.ReservationId = ""
	case *streamv1.StockCover:
		v.RiskChanged = false 
	case *fraudv1.FraudReviewRequested:
		v.Reasons = nil
	}
}
