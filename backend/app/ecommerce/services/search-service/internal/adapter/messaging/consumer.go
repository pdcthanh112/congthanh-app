// Package messaging tiêu thụ ecom.product.events để cập nhật chỉ mục tìm kiếm.
package messaging

import (
	"context"
	"log/slog"
	"math"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

const TopicProductEvents = "ecom.product.events"

var (
	upsertedType = string((&productv1.ProductUpserted{}).ProtoReflect().Descriptor().FullName())
	deletedType  = string((&productv1.ProductDeleted{}).ProtoReflect().Descriptor().FullName())
)

type Indexer interface {
	Apply(ctx context.Context, p domain.Product, at time.Time) error
	Remove(ctx context.Context, id string, at time.Time) error
}

// Handler không dùng transaction DB: ghi OpenSearch theo version (thời điểm event) nên xử lý lại vẫn an toàn.
func Handler(ix Indexer) func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
	return func(ctx context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		at := env.GetOccurredAt().AsTime()
		if env.GetOccurredAt() == nil {
			at = time.Now()
		}
		switch env.GetType() {
		case upsertedType:
			ev := &productv1.ProductUpserted{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad ProductUpserted payload", "err", err)
				return nil
			}
			cur := ev.GetPrice().GetCurrencyCode()
			price := float64(money.ToMinor(ev.GetPrice())) / math.Pow10(money.MinorDigits(cur))
			return ix.Apply(ctx, domain.Product{
				ID: ev.GetProductId(), SKU: ev.GetSku(), Name: ev.GetName(), Slug: ev.GetSlug(),
				CategoryID: ev.GetCategoryId(), Price: price, Currency: cur, Status: ev.GetStatus(),
			}, at)
		case deletedType:
			ev := &productv1.ProductDeleted{}
			if err := env.GetPayload().UnmarshalTo(ev); err != nil {
				slog.WarnContext(ctx, "bad ProductDeleted payload", "err", err)
				return nil
			}
			return ix.Remove(ctx, ev.GetProductId(), at)
		}
		return nil
	}
}
