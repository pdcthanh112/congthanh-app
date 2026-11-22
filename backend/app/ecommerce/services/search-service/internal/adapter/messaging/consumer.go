package messaging

import (
	"context"
	"log/slog"
	"math"
	"strconv"
	"strings"
	"time"

	"github.com/jackc/pgx/v5"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
	productv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/product/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

const TopicProductEvents = "ecommerce.product.events"

var (
	upsertedType = string((&productv1.ProductUpserted{}).ProtoReflect().Descriptor().FullName())
	deletedType  = string((&productv1.ProductDeleted{}).ProtoReflect().Descriptor().FullName())
)

type Indexer interface {
	Apply(ctx context.Context, p domain.Product, at time.Time) error
	Remove(ctx context.Context, id string, at time.Time) error
}

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
			// Variant không có tài liệu riêng: sản phẩm gốc phát lại kèm variant mỗi khi variant đổi.
			if ev.GetParentId() != "" {
				return ix.Remove(ctx, ev.GetProductId(), at)
			}
			return ix.Apply(ctx, ToProduct(ev), at)
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

// ToProduct dựng tài liệu chỉ mục của một sản phẩm gốc: thuộc tính lọc được thành facet, thuộc tính searchable vào
// văn bản tìm kiếm, variant đang bán góp SKU, khoảng giá và giá trị option.
func ToProduct(ev *productv1.ProductUpserted) domain.Product {
	p := domain.Product{
		ID: ev.GetProductId(), SKU: ev.GetSku(), Name: ev.GetName(), Slug: ev.GetSlug(),
		CategoryID: ev.GetCategoryId(), CategoryIDs: ev.GetCategoryIds(), Status: ev.GetStatus(), Featured: ev.GetFeatured(),
	}
	if len(p.CategoryIDs) == 0 && p.CategoryID != "" {
		p.CategoryIDs = []string{p.CategoryID}
	}
	if ev.GetBrandId() != 0 {
		p.BrandID = strconv.FormatInt(ev.GetBrandId(), 10)
	}
	if ev.GetSku() != "" {
		p.SKUs = append(p.SKUs, ev.GetSku())
	}
	var text []string
	for _, a := range ev.GetAttributes() {
		if a.GetSearchable() && a.GetValue() != "" {
			text = append(text, a.GetValue())
		}
		if !a.GetFilterable() || a.GetCode() == "" {
			continue
		}
		attr := domain.Attribute{Code: a.GetCode(), Name: a.GetName(), Value: a.GetValue()}
		if a.Number != nil {
			n := a.GetNumber()
			attr.Number = &n
		}
		p.Attributes = append(p.Attributes, attr)
	}
	p.SearchText = strings.Join(text, " ")

	if ev.GetPrice() != nil && ev.GetPrice().GetCurrencyCode() != "" {
		p.Currency = ev.GetPrice().GetCurrencyCode()
		p.Price = amount(ev.GetPrice())
		p.PriceMax = p.Price
	}
	seen := map[domain.Option]bool{}
	var vmin, vmax float64
	priced := false
	for _, v := range ev.GetVariants() {
		if !strings.EqualFold(v.GetStatus(), "ACTIVE") {
			continue
		}
		if v.GetSku() != "" {
			p.SKUs = append(p.SKUs, v.GetSku())
		}
		for _, o := range v.GetOptions() {
			opt := domain.Option{Name: o.GetOptionName(), Value: o.GetValue()}
			if opt.Name != "" && opt.Value != "" && !seen[opt] {
				seen[opt] = true
				p.Options = append(p.Options, opt)
			}
		}
		// Có variant thì khoảng giá lấy từ các variant đang bán (sản phẩm gốc không bán trực tiếp), cùng currency với
		// sản phẩm gốc, hoặc với variant có giá đầu tiên nếu gốc không có giá; khác currency thì bỏ, không quy đổi.
		vp := v.GetPrice()
		if vp == nil || vp.GetCurrencyCode() == "" {
			continue
		}
		if p.Currency == "" {
			p.Currency = vp.GetCurrencyCode()
		}
		if vp.GetCurrencyCode() != p.Currency {
			slog.Warn("variant price in another currency left out of the price range",
				"product", p.ID, "variant", v.GetProductId(), "currency", vp.GetCurrencyCode())
			continue
		}
		a := amount(vp)
		if !priced {
			vmin, vmax, priced = a, a, true
		} else {
			vmin, vmax = math.Min(vmin, a), math.Max(vmax, a)
		}
	}
	if priced {
		p.Price, p.PriceMax = vmin, vmax
	}
	return p
}

func amount(m *commonv1.Money) float64 {
	return float64(money.ToMinor(m)) / math.Pow10(money.MinorDigits(m.GetCurrencyCode()))
}
