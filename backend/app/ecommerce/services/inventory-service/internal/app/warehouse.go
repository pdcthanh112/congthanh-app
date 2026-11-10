package app

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

type WarehouseInput struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Location string `json:"location"`
	Address  string `json:"address"`
	Active   *bool  `json:"isActive"`
}

func (s *Service) ListWarehouses(ctx context.Context) ([]domain.Warehouse, error) {
	return s.warehouses.List(ctx)
}

func (s *Service) GetWarehouse(ctx context.Context, id string) (domain.Warehouse, error) {
	return s.warehouses.Get(ctx, id)
}

func (s *Service) CreateWarehouse(ctx context.Context, in WarehouseInput) (domain.Warehouse, error) {
	w, err := domain.NewWarehouse(in.ID, in.Name, in.Location, in.Address, in.Active == nil || *in.Active)
	if err != nil {
		return domain.Warehouse{}, err
	}
	return s.warehouses.Create(ctx, w)
}

func (s *Service) UpdateWarehouse(ctx context.Context, id string, in WarehouseInput) (domain.Warehouse, error) {
	cur, err := s.warehouses.Get(ctx, id)
	if err != nil {
		return domain.Warehouse{}, err
	}
	if in.Name != "" {
		cur.Name = in.Name
	}
	if in.Location != "" {
		cur.Location = in.Location
	}
	if in.Address != "" {
		cur.Address = in.Address
	}
	if in.Active != nil {
		cur.Active = *in.Active
	}
	return s.warehouses.Update(ctx, cur)
}

func (s *Service) DeleteWarehouse(ctx context.Context, id string) error {
	return s.warehouses.Delete(ctx, id)
}
