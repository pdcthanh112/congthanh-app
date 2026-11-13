package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
)

const TopicOrderEvents = "ecommerce.order.events"

var (
	orderPlacedType    = string((&orderv1.OrderPlaced{}).ProtoReflect().Descriptor().FullName())
	orderConfirmedType = string((&orderv1.OrderConfirmed{}).ProtoReflect().Descriptor().FullName())
	orderCancelledType = string((&orderv1.OrderCancelled{}).ProtoReflect().Descriptor().FullName())
)

// OrderTracker là phần use case mà consumer cần.
type OrderTracker interface {
	OrderPlaced(ctx context.Context, orderID, customerID string, productIDs []string) error
	OrderConfirmed(ctx context.Context, orderID string) (int, error)
	OrderCancelled(ctx context.Context, orderID string) error
}

// OrderEventsHandler: OrderPlaced chỉ ghi nhớ sản phẩm; OrderConfirmed (đã thanh toán) mới bỏ chúng khỏi giỏ
// mặc định; OrderCancelled thì giữ nguyên giỏ để khách đặt lại.
func OrderEventsHandler(c OrderTracker) func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		switch env.GetType() {
		case orderPlacedType:
			ev := &orderv1.OrderPlaced{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad OrderPlaced payload", "err", err)
				return nil
			}
			ids := make([]string, 0, len(ev.GetLines()))
			for _, l := range ev.GetLines() {
				ids = append(ids, l.GetProductId())
			}
			return c.OrderPlaced(ctx, ev.GetOrderId(), ev.GetCustomerId(), ids)
		case orderConfirmedType:
			ev := &orderv1.OrderConfirmed{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad OrderConfirmed payload", "err", err)
				return nil
			}
			n, err := c.OrderConfirmed(ctx, ev.GetOrderId())
			if n > 0 {
				slog.InfoContext(ctx, "removed ordered items from cart", "order_id", ev.GetOrderId(), "items", n)
			}
			return err
		case orderCancelledType:
			ev := &orderv1.OrderCancelled{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad OrderCancelled payload", "err", err)
				return nil
			}
			return c.OrderCancelled(ctx, ev.GetOrderId())
		}
		return nil
	}
}
