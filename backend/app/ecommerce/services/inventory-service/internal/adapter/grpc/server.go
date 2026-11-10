// Package grpc là driving adapter gRPC (ecommerce.inventory.v1.InventoryService).
package grpc

import (
	"context"
	"errors"
	"time"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/auth"
	pb "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

type Server struct {
	pb.UnimplementedInventoryServiceServer
	svc *app.Service
}

func New(svc *app.Service) *Server { return &Server{svc: svc} }

func toItems(in []*pb.InventoryItem) []domain.Item {
	out := make([]domain.Item, len(in))
	for i, it := range in {
		out[i] = domain.Item{ProductID: it.GetProductId(), Quantity: it.GetQuantity()}
	}
	return out
}

// grpcErr đổi lỗi nghiệp vụ sang mã gRPC tương ứng.
func grpcErr(err error) error {
	switch domain.KindOf(err) {
	case domain.KindInvalid:
		return status.Error(codes.InvalidArgument, err.Error())
	case domain.KindNotFound:
		return status.Error(codes.NotFound, err.Error())
	case domain.KindConflict:
		return status.Error(codes.FailedPrecondition, err.Error())
	}
	if errors.Is(err, context.Canceled) || errors.Is(err, context.DeadlineExceeded) {
		return status.FromContextError(err).Err()
	}
	return status.Error(codes.Internal, "internal error")
}

// GetInventoryByProduct (API cũ): tổng tồn kho khả dụng theo SKU.
func (s *Server) GetInventoryByProduct(ctx context.Context, req *pb.InventoryRequest) (*pb.InventoryResponse, error) {
	stocks, err := s.svc.StocksBySKU(ctx, req.GetSku())
	if err != nil {
		return nil, grpcErr(err)
	}
	var total int64
	for _, st := range stocks {
		if !st.Discontinued {
			total += int64(st.Available())
		}
	}
	return &pb.InventoryResponse{Id: stocks[0].ID, Sku: req.GetSku(), Stock: total}, nil
}

func (s *Server) CheckAvailability(ctx context.Context, req *pb.CheckAvailabilityRequest) (*pb.CheckAvailabilityResponse, error) {
	av, err := s.svc.CheckAvailability(ctx, toItems(req.GetItems()), req.GetWarehouseId())
	if err != nil {
		return nil, grpcErr(err)
	}
	resp := &pb.CheckAvailabilityResponse{AllAvailable: true}
	for _, a := range av {
		r := &pb.AvailabilityResult{
			ProductId:         a.ProductID,
			RequestedQuantity: a.Requested,
			AvailableQuantity: a.Available,
			IsAvailable:       a.IsAvailable(),
			ReservedQuantity:  a.Reserved,
			InTransitQuantity: a.InTransit,
		}
		for _, st := range a.Stocks {
			r.WarehouseStocks = append(r.WarehouseStocks, &pb.WarehouseStock{
				WarehouseId: st.WarehouseID, WarehouseName: st.WarehouseName,
				Quantity: max(st.Available(), 0), Location: st.Location,
			})
		}
		if !r.IsAvailable {
			resp.AllAvailable = false
			resp.UnavailableProducts = append(resp.UnavailableProducts, a.ProductID)
		}
		resp.Results = append(resp.Results, r)
	}
	return resp, nil
}

func (s *Server) ReserveInventory(ctx context.Context, req *pb.ReserveInventoryRequest) (*pb.ReserveInventoryResponse, error) {
	in := app.ReserveInput{
		ID:         req.GetReservationId(),
		CustomerID: req.GetCustomerId(),
		Type:       domain.ParseReservationType(req.GetReservationType().String()),
		Items:      toItems(req.GetItems()),
	}
	if req.GetExpiryTime() > 0 {
		in.ExpiresAt = time.Unix(req.GetExpiryTime(), 0)
	}
	res, err := s.svc.Reserve(ctx, in)
	if err != nil {
		return nil, grpcErr(err)
	}
	out := &pb.ReserveInventoryResponse{
		Success: res.Success, ReservationId: res.ID, Message: res.Message,
		ReservedAt: res.ReservedAt.Unix(), ExpiresAt: res.ExpiresAt.Unix(),
	}
	for _, l := range res.Lines {
		out.Results = append(out.Results, &pb.ReservationResult{
			ProductId: l.ProductID, RequestedQuantity: l.Requested, ReservedQuantity: l.Reserved,
			FullyReserved: l.Reserved >= l.Requested, WarehouseId: l.WarehouseID, ErrorMessage: l.Error,
		})
	}
	return out, nil
}

func (s *Server) ReleaseInventory(ctx context.Context, req *pb.ReleaseInventoryRequest) (*pb.ReleaseInventoryResponse, error) {
	timeout := req.GetReleaseType() == pb.ReleaseInventoryRequest_TIMEOUT
	res, err := s.svc.Release(ctx, req.GetReservationId(), req.GetProductIds(), req.GetReason(), timeout)
	if err != nil {
		return nil, grpcErr(err)
	}
	out := &pb.ReleaseInventoryResponse{Success: res.Success, ReservationId: res.ID, Message: res.Message, ReleasedAt: res.ReleasedAt.Unix()}
	for _, l := range res.Lines {
		out.Results = append(out.Results, &pb.ReleaseResult{ProductId: l.ProductID, ReleasedQuantity: l.Released, Success: true})
	}
	return out, nil
}

func (s *Server) CommitReservation(ctx context.Context, req *pb.CommitReservationRequest) (*pb.CommitReservationResponse, error) {
	res, err := s.svc.Commit(ctx, req.GetReservationId(), req.GetOrderId(), toItems(req.GetItems()))
	if err != nil {
		return nil, grpcErr(err)
	}
	out := &pb.CommitReservationResponse{Success: res.Success, ReservationId: res.ID, Message: res.Message, CommittedAt: res.CommittedAt.Unix()}
	for _, l := range res.Lines {
		out.Results = append(out.Results, &pb.CommitResult{
			ProductId: l.ProductID, CommittedQuantity: l.Committed, NewAvailableQuantity: l.NewAvailable, Success: true,
		})
	}
	return out, nil
}

func (s *Server) GetInventoryLevel(ctx context.Context, req *pb.GetInventoryLevelRequest) (*pb.GetInventoryLevelResponse, error) {
	levels, err := s.svc.Levels(ctx, req.GetProductIds(), req.GetWarehouseId())
	if err != nil {
		return nil, grpcErr(err)
	}
	out := &pb.GetInventoryLevelResponse{}
	for _, l := range levels {
		lv := &pb.InventoryLevel{
			ProductId: l.ProductID, AvailableQuantity: l.Available, TotalQuantity: l.Total,
			ReorderPoint: l.ReorderPoint, ReorderQuantity: l.ReorderQuantity,
			Status:      pb.InventoryLevel_InventoryStatus(pb.InventoryLevel_InventoryStatus_value[string(l.Status)]),
			LastUpdated: l.LastUpdated.Unix(),
		}
		if req.GetIncludeReserved() {
			lv.ReservedQuantity = l.Reserved
		}
		if req.GetIncludeInTransit() {
			lv.InTransitQuantity = l.InTransit
		}
		out.Levels = append(out.Levels, lv)
	}
	return out, nil
}

func (s *Server) AdjustInventory(ctx context.Context, req *pb.AdjustInventoryRequest) (*pb.AdjustInventoryResponse, error) {
	adjs := make([]app.Adjustment, len(req.GetAdjustments()))
	for i, a := range req.GetAdjustments() {
		adjs[i] = app.Adjustment{
			ProductID: a.GetProductId(), WarehouseID: a.GetWarehouseId(),
			Change: a.GetQuantityChange(), Type: a.GetAdjustmentType().String(),
		}
	}
	actor := req.GetAdjustedBy()
	if p, ok := auth.FromContext(ctx); ok && actor == "" {
		actor = p.Subject
	}
	res, err := s.svc.Adjust(ctx, adjs, req.GetReason(), req.GetReferenceId(), actor)
	if err != nil {
		return nil, grpcErr(err)
	}
	out := &pb.AdjustInventoryResponse{Success: res.Success, Message: res.Message}
	for _, l := range res.Lines {
		out.Results = append(out.Results, &pb.AdjustmentResult{
			ProductId: l.ProductID, OldQuantity: l.Old, Adjustment: l.Change, NewQuantity: l.New,
			Success: l.Success, ErrorMessage: l.Error,
		})
	}
	return out, nil
}
