// Package messaging: đồng bộ sản phẩm từ ecommerce.product.events (giá khởi tạo, xoá sản phẩm).
package messaging

import (
	"context"
	"log/slog"

	"github.com/jackc/pgx/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
)

const TopicProductEvents = "ecommerce.product.events"

var (
	upsertedType = string((&productv1.ProductUpserted{}).ProtoReflect().Descriptor().FullName())
	deletedType  = string((&productv1.ProductDeleted{}).ProtoReflect().Descriptor().FullName())
)

type Catalog interface {
	SeedFromCatalog(ctx context.Context, productID, currency string, amount int64) error
	RemoveProduct(ctx context.Context, productID string) error
}

// OnChange được gọi sau khi dữ liệu giá đổi (vô hiệu cache).
func Handler(c Catalog, onChange func(context.Context)) func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		switch env.GetType() {
		case upsertedType:
			ev := &productv1.ProductUpserted{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad ProductUpserted payload", "err", err)
				return nil
			}
			p := ev.GetPrice()
			if p == nil || p.GetCurrencyCode() == "" || ev.GetProductId() == "" {
				return nil
			}
			if err := c.SeedFromCatalog(ctx, ev.GetProductId(), p.GetCurrencyCode(), money.ToMinor(p)); err != nil {
				return err
			}
		case deletedType:
			ev := &productv1.ProductDeleted{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad ProductDeleted payload", "err", err)
				return nil
			}
			if err := c.RemoveProduct(ctx, ev.GetProductId()); err != nil {
				return err
			}
		default:
			return nil
		}
		onChange(ctx)
		return nil
	}
}
