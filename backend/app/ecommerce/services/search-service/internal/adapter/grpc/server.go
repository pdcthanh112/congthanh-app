// Package grpc là driving adapter gRPC (ecommerce.search.v1.SearchService) cho storefront-bff.
package grpc

import (
	"context"
	"math"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/search/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/search-service/internal/domain"
)

type Server struct {
	pb.UnimplementedSearchServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

func (s *Server) SearchProducts(ctx context.Context, req *pb.SearchProductsRequest) (*pb.SearchProductsResponse, error) {
	res, err := s.svc.Search(ctx, domain.Query{
		Text: req.GetQuery(), CategoryID: req.GetCategoryId(), MinPrice: req.GetMinPrice(), MaxPrice: req.GetMaxPrice(),
		Sort: domain.Sort(req.GetSort()), Page: int(req.GetPage()), Size: int(req.GetPageSize()),
	})
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	out := &pb.SearchProductsResponse{Total: res.Total}
	for _, h := range res.Hits {
		minor := int64(math.Round(h.Price * math.Pow10(money.MinorDigits(h.Currency))))
		out.Hits = append(out.Hits, &pb.ProductHit{
			ProductId: h.ID, Name: h.Name, Slug: h.Slug, CategoryId: h.CategoryID,
			Price: money.FromMinor(minor, h.Currency), Score: float32(h.Score),
		})
	}
	for _, f := range res.Categories {
		out.Categories = append(out.Categories, &pb.CategoryFacet{CategoryId: f.CategoryID, Count: f.Count})
	}
	return out, nil
}

func (s *Server) Suggest(ctx context.Context, req *pb.SuggestRequest) (*pb.SuggestResponse, error) {
	out, err := s.svc.Suggest(ctx, req.GetPrefix(), int(req.GetLimit()))
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	return &pb.SuggestResponse{Suggestions: out}, nil
}
