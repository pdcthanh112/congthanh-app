// Package messaging: đơn đã đặt → tạo vận đơn; đơn bị huỷ → huỷ vận đơn chưa lấy hàng.
package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

const TopicOrderEvents = "ecom.order.events"

var (
	placedType    = string((&orderv1.OrderPlaced{}).ProtoReflect().Descriptor().FullName())
	cancelledType = string((&orderv1.OrderCancelled{}).ProtoReflect().Descriptor().FullName())
)

type OrderHandler interface {
	OnOrderPlaced(ctx context.Context, orderID, customerID string, addr *domain.Address) error
	OnOrderCancelled(ctx context.Context, orderID string) error
}

func AddressFromPB(a *orderv1.ShippingAddress) *domain.Address {
	if a == nil || a.GetCountryCode() == "" {
		return nil
	}
	return &domain.Address{
		RecipientName: a.GetRecipientName(), Phone: a.GetPhone(), Line1: a.GetLine1(), Line2: a.GetLine2(),
		Ward: a.GetWard(), District: a.GetDistrict(), RegionCode: a.GetRegionCode(), PostalCode: a.GetPostalCode(), CountryCode: a.GetCountryCode(),
	}
}

// Handler: use case dùng transaction riêng (idempotent theo order_id), processed_events chống xử lý trùng.
func Handler(h OrderHandler) func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		switch env.GetType() {
		case placedType:
			ev := &orderv1.OrderPlaced{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad OrderPlaced payload", "err", err)
				return nil
			}
			return h.OnOrderPlaced(ctx, ev.GetOrderId(), ev.GetCustomerId(), AddressFromPB(ev.GetShippingAddress()))
		case cancelledType:
			ev := &orderv1.OrderCancelled{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad OrderCancelled payload", "err", err)
				return nil
			}
			return h.OnOrderCancelled(ctx, ev.GetOrderId())
		}
		return nil
	}
}
