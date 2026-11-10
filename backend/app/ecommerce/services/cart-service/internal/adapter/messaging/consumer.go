package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
)

const TopicOrderEvents = "ecom.order.events"

var orderPlacedType = string((&orderv1.OrderPlaced{}).ProtoReflect().Descriptor().FullName())

// OrderClearer là phần use case mà consumer cần.
type OrderClearer interface {
	ClearOrdered(ctx context.Context, customerID string, productIDs []string) (int, error)
}

// OrderEventsHandler: đơn đã đặt → bỏ các sản phẩm đó khỏi giỏ mặc định của khách.
func OrderEventsHandler(c OrderClearer) func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		if env.GetType() != orderPlacedType {
			return nil
		}
		ev := &orderv1.OrderPlaced{}
		if err := env.GetPayload().UnmarshalTo(ev); err != nil {
			slog.WarnContext(ctx, "bad OrderPlaced payload", "err", err)
			return nil
		}
		ids := make([]string, 0, len(ev.GetLines()))
		for _, l := range ev.GetLines() {
			ids = append(ids, l.GetProductId())
		}
		n, err := c.ClearOrdered(ctx, ev.GetCustomerId(), ids)
		if n > 0 {
			slog.InfoContext(ctx, "removed ordered items from cart", "order_id", ev.GetOrderId(), "items", n)
		}
		return err
	}
}
