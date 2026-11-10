// Package grpc là driving adapter gRPC (ecommerce.cart.v1.CartService) cho order-service khi checkout.
package grpc

import (
	"context"
	"errors"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/types/known/timestamppb"

	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/cart/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

type Server struct {
	pb.UnimplementedCartServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

func grpcErr(err error) error {
	switch domain.KindOf(err) {
	case domain.KindInvalid:
		return status.Error(codes.InvalidArgument, err.Error())
	case domain.KindNotFound:
		return status.Error(codes.NotFound, err.Error())
	case domain.KindConflict:
		return status.Error(codes.FailedPrecondition, err.Error())
	case domain.KindUnavailable:
		return status.Error(codes.Unavailable, err.Error())
	}
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return status.FromContextError(err).Err()
	}
	return status.Error(codes.Internal, "internal error")
}

func toPB(c domain.Cart) *pb.Cart {
	out := &pb.Cart{
		Id: c.ID, Name: c.Name, CustomerId: c.CustomerID, Status: string(c.Status), IsDefault: c.IsDefault,
		CreatedAt: timestamppb.New(c.CreatedAt), UpdatedAt: timestamppb.New(c.UpdatedAt),
	}
	for _, it := range c.Items {
		out.Items = append(out.Items, &pb.CartItem{Id: it.ID, ProductId: it.ProductID, Quantity: it.Quantity, CreatedAt: timestamppb.New(it.CreatedAt)})
	}
	return out
}

func (s *Server) GetCart(ctx context.Context, req *pb.GetCartRequest) (*pb.Cart, error) {
	c, err := s.svc.Get(ctx, req.GetCartId())
	if err != nil {
		return nil, grpcErr(err)
	}
	return toPB(c), nil
}

func (s *Server) GetDefaultCart(ctx context.Context, req *pb.GetDefaultCartRequest) (*pb.Cart, error) {
	c, err := s.svc.GetDefault(ctx, req.GetCustomerId())
	if err != nil {
		return nil, grpcErr(err)
	}
	return toPB(c), nil
}

func (s *Server) ClearCart(ctx context.Context, req *pb.ClearCartRequest) (*pb.ClearCartResponse, error) {
	n, err := s.svc.Clear(ctx, req.GetCartId(), req.GetProductIds())
	if err != nil {
		return nil, grpcErr(err)
	}
	return &pb.ClearCartResponse{Success: true, RemovedItems: int32(n)}, nil
}
