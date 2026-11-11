// Package grpc là driving adapter gRPC (ecommerce.pricing.v1.PricingService) cho checkout, cart, search, storefront-bff.
package grpc

import (
	"context"

	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/pricing/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
)

type Server struct {
	pb.UnimplementedPricingServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

// Gọi nội bộ: tin nhóm khách do service gọi truyền vào (đã xác thực khách ở biên).
func ctxFrom(c *pb.PricingContext) app.Context {
	pc := app.Context{Currency: c.GetCurrencyCode(), Groups: c.GetCustomerGroups()}
	if c.GetAt() != nil {
		pc.At = c.GetAt().AsTime()
	}
	return pc
}

func toPB(r domain.Resolved) *pb.Price {
	p := &pb.Price{ProductId: r.ProductID, Price: money.FromMinor(r.Amount, r.Currency), DiscountPercent: int32(r.DiscountPercent),
		PriceList: r.PriceList, SaleKind: string(r.SaleKind)}
	if r.CompareAt > r.Amount {
		p.CompareAt = money.FromMinor(r.CompareAt, r.Currency)
	}
	if r.SaleEndsAt != nil {
		p.SaleEndsAt = timestamppb.New(*r.SaleEndsAt)
	}
	if r.Lowest30d != nil {
		p.Lowest_30D = money.FromMinor(*r.Lowest30d, r.Currency)
	}
	return p
}

func (s *Server) GetPrices(ctx context.Context, req *pb.GetPricesRequest) (*pb.GetPricesResponse, error) {
	res, err := s.svc.Prices(ctx, req.GetProductIds(), ctxFrom(req.GetContext()))
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	out := &pb.GetPricesResponse{}
	seen := map[string]bool{}
	for _, id := range req.GetProductIds() {
		if r, ok := res[id]; ok && !seen[id] {
			seen[id] = true
			out.Prices = append(out.Prices, toPB(r))
		}
	}
	return out, nil
}

func (s *Server) QuoteLines(ctx context.Context, req *pb.QuoteLinesRequest) (*pb.QuoteLinesResponse, error) {
	lines := make([]app.Line, 0, len(req.GetLines()))
	for _, l := range req.GetLines() {
		lines = append(lines, app.Line{LineID: l.GetLineId(), ProductID: l.GetProductId(), Quantity: int(l.GetQuantity())})
	}
	q, err := s.svc.QuoteLines(ctx, lines, ctxFrom(req.GetContext()))
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	out := &pb.QuoteLinesResponse{Subtotal: money.FromMinor(q.Subtotal, q.Currency), UnpricedProductIds: q.Unpriced}
	for _, l := range q.Lines {
		ql := &pb.QuotedLine{LineId: l.LineID, ProductId: l.ProductID, Quantity: int32(l.Quantity),
			UnitPrice: money.FromMinor(l.UnitPrice, q.Currency), LineTotal: money.FromMinor(l.LineTotal, q.Currency), PriceList: l.PriceList}
		if l.CompareAt > l.UnitPrice {
			ql.CompareAt = money.FromMinor(l.CompareAt, q.Currency)
		}
		out.Lines = append(out.Lines, ql)
	}
	return out, nil
}
