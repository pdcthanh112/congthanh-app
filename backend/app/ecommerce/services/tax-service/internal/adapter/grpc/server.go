// Package grpc là driving adapter gRPC (ecommerce.tax.v1.TaxService) cho order-service khi checkout.
package grpc

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/tax/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/domain"
)

type Server struct {
	pb.UnimplementedTaxServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

func addr(a *pb.TaxAddress) domain.Address {
	return domain.Address{CountryCode: a.GetCountryCode(), RegionCode: a.GetRegionCode(), PostalCode: a.GetPostalCode()}
}

func (s *Server) CalculateTax(ctx context.Context, req *pb.CalculateTaxRequest) (*pb.CalculateTaxResponse, error) {
	lines := make([]domain.Line, 0, len(req.GetLines()))
	for _, l := range req.GetLines() {
		lines = append(lines, domain.Line{
			LineID: l.GetLineId(), TaxClassID: l.GetTaxClassId(),
			TaxableMinor: money.ToMinor(l.GetTaxableAmount()), Currency: l.GetTaxableAmount().GetCurrencyCode(),
		})
	}
	out, total, err := s.svc.Calculate(ctx, addr(req.GetAddress()), lines)
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	cur := ""
	if len(lines) > 0 {
		cur = lines[0].Currency
	}
	res := &pb.CalculateTaxResponse{TotalTax: money.FromMinor(total, cur)}
	for _, l := range out {
		res.Lines = append(res.Lines, &pb.LineTax{LineId: l.LineID, RatePercent: l.RatePercent, Tax: money.FromMinor(l.TaxMinor, l.Currency)})
	}
	return res, nil
}

func (s *Server) GetTaxRates(ctx context.Context, req *pb.GetTaxRatesRequest) (*pb.GetTaxRatesResponse, error) {
	rates, err := s.svc.Rates(ctx, req.GetTaxClassIds(), addr(req.GetAddress()))
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	res := &pb.GetTaxRatesResponse{}
	for _, r := range rates {
		res.Rates = append(res.Rates, &pb.TaxClassRate{TaxClassId: r.TaxClassID, RatePercent: r.RatePercent, Matched: r.Matched})
	}
	return res, nil
}
