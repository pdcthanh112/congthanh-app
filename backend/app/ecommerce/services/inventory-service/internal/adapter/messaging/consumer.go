package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

const TopicOrderEvents = "ecommerce.order.events"

// Releaser là phần use case mà consumer cần.
type Releaser interface {
	Release(ctx context.Context, id string, productIDs []string, reason string, expired bool) (domain.ReleaseResult, error)
}

// OrderEventsHandler: đơn bị huỷ → trả hàng đã giữ (idempotent).
// Việc release chạy transaction riêng; bảng processed_events (tx của consumer) chống xử lý trùng.
func OrderEventsHandler(r Releaser) func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		if env.GetType() != string((&orderv1.OrderCancelled{}).ProtoReflect().Descriptor().FullName()) {
			return nil
		}
		ev := &orderv1.OrderCancelled{}
		if err := env.GetPayload().UnmarshalTo(ev); err != nil {
			slog.WarnContext(ctx, "bad OrderCancelled payload", "err", err)
			return nil
		}
		if ev.GetReservationId() == "" {
			return nil
		}
		_, err := r.Release(ctx, ev.GetReservationId(), nil, "order cancelled: "+ev.GetReason(), false)
		if domain.KindOf(err) == domain.KindNotFound {
			return nil
		}
		return err
	}
}
