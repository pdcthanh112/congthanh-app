// Package grpc là driving adapter gRPC (ecommerce.shipping.v1.ShippingService) cho order-service.
package grpc

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	orderv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/order/v1"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/shipping/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/shipping-service/internal/domain"
)

type Server struct {
	pb.UnimplementedShippingServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

func (s *Server) QuoteShipping(ctx context.Context, req *pb.QuoteShippingRequest) (*pb.QuoteShippingResponse, error) {
	dest := messaging.AddressFromPB(req.GetDestination())
	if dest == nil {
		return nil, apperr.ToGRPC(apperr.Invalid("destination countryCode is required"))
	}
	v := req.GetParcel().GetDeclaredValue()
	qs, err := s.svc.Quote(ctx, *dest, domain.Parcel{WeightGrams: int(req.GetParcel().GetWeightGrams()), DeclaredValue: money.ToMinor(v), Currency: v.GetCurrencyCode()})
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	out := &pb.QuoteShippingResponse{}
	for _, q := range qs {
		out.Quotes = append(out.Quotes, &pb.ShippingQuote{Carrier: q.Carrier, ServiceCode: q.ServiceCode, ServiceName: q.ServiceName,
			Fee: money.FromMinor(q.Fee, q.Currency), EstimatedDaysMin: int32(q.DaysMin), EstimatedDaysMax: int32(q.DaysMax)})
	}
	return out, nil
}

func (s *Server) GetShipment(ctx context.Context, req *pb.GetShipmentRequest) (*pb.GetShipmentResponse, error) {
	// Gọi nội bộ giữa service: không lọc theo chủ sở hữu (order-service đã kiểm quyền với đơn hàng).
	sh, err := s.svc.ForOrder(ctx, req.GetOrderId(), "", true)
	if err != nil {
		return nil, apperr.ToGRPC(err)
	}
	out := &pb.Shipment{Id: sh.ID, OrderId: sh.OrderID, Carrier: sh.Carrier, ServiceCode: sh.ServiceCode, Status: string(sh.Status)}
	if sh.TrackingNumber != nil {
		out.TrackingNumber = *sh.TrackingNumber
	}
	if a := sh.Address; a != nil {
		out.Address = &orderv1.ShippingAddress{RecipientName: a.RecipientName, Phone: a.Phone, Line1: a.Line1, Line2: a.Line2,
			Ward: a.Ward, District: a.District, RegionCode: a.RegionCode, PostalCode: a.PostalCode, CountryCode: a.CountryCode}
	}
	return &pb.GetShipmentResponse{Shipment: out}, nil
}
