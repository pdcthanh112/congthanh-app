package app

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

func (s *Service) StocksBySKU(ctx context.Context, sku string) ([]domain.Stock, error) {
	st, err := s.read.StocksBySKU(ctx, sku)
	if err != nil {
		return nil, err
	}
	if len(st) == 0 {
		return nil, domain.NotFound("Inventory item not found for SKU: %s", sku)
	}
	return st, nil
}

func (s *Service) CheckAvailability(ctx context.Context, items []domain.Item, warehouseID string) ([]domain.Availability, error) {
	merged, err := domain.MergeItems(items)
	if err != nil {
		return nil, err
	}
	ids := make([]string, len(merged))
	for i, it := range merged {
		ids[i] = it.ProductID
	}
	stocks, err := s.read.StocksByProduct(ctx, ids, warehouseID)
	if err != nil {
		return nil, err
	}
	by := groupByProduct(stocks)
	out := make([]domain.Availability, 0, len(merged))
	for _, it := range merged {
		out = append(out, domain.ComputeAvailability(it, by[it.ProductID]))
	}
	return out, nil
}

func (s *Service) Levels(ctx context.Context, productIDs []string, warehouseID string) ([]domain.Level, error) {
	if len(productIDs) == 0 {
		return nil, domain.Invalid("productIds must not be empty")
	}
	stocks, err := s.read.StocksByProduct(ctx, productIDs, warehouseID)
	if err != nil {
		return nil, err
	}
	by := groupByProduct(stocks)
	out := make([]domain.Level, 0, len(productIDs))
	for _, p := range productIDs {
		out = append(out, domain.ComputeLevel(p, by[p]))
	}
	return out, nil
}

func groupByProduct(stocks []domain.Stock) map[string][]domain.Stock {
	by := map[string][]domain.Stock{}
	for _, st := range stocks {
		by[st.ProductID] = append(by[st.ProductID], st)
	}
	return by
}

type Adjustment struct {
	ProductID   string `json:"productId"`
	SKU         string `json:"sku"`
	WarehouseID string `json:"warehouseId"`
	Change      int32  `json:"quantityChange"`
	Type        string `json:"adjustmentType"`
}

type AdjustLine struct {
	ProductID string `json:"productId"`
	Old       int32  `json:"oldQuantity"`
	Change    int32  `json:"adjustment"`
	New       int32  `json:"newQuantity"`
	Success   bool   `json:"success"`
	Error     string `json:"errorMessage,omitempty"`
}

type AdjustResult struct {
	Success bool         `json:"success"`
	Lines   []AdjustLine `json:"results"`
	Message string       `json:"message"`
}

// Adjust điều chỉnh tồn kho; mỗi dòng độc lập, dòng vi phạm nghiệp vụ được báo lỗi và bỏ qua.
func (s *Service) Adjust(ctx context.Context, adjs []Adjustment, reason, ref, actor string) (AdjustResult, error) {
	if len(adjs) == 0 {
		return AdjustResult{}, domain.Invalid("adjustments must not be empty")
	}
	res := AdjustResult{Success: true}
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		res.Lines = res.Lines[:0]
		for _, a := range adjs {
			line, err := s.adjustOne(ctx, tx, a, reason, ref, actor)
			if err != nil {
				return err
			}
			res.Lines = append(res.Lines, line)
			res.Success = res.Success && line.Success
		}
		return nil
	})
	if err != nil {
		return AdjustResult{}, err
	}
	res.Message = "inventory adjusted"
	if !res.Success {
		res.Message = "some adjustments failed"
	}
	return res, nil
}

func (s *Service) adjustOne(ctx context.Context, tx Tx, a Adjustment, reason, ref, actor string) (AdjustLine, error) {
	line := AdjustLine{ProductID: a.ProductID, Change: a.Change}
	typ, err := domain.ParseAdjustmentType(a.Type)
	if a.ProductID == "" {
		err = domain.Invalid("productId is required")
	}
	if err != nil {
		line.Error = err.Error()
		return line, nil
	}
	if a.WarehouseID == "" {
		a.WarehouseID = s.opt.DefaultWarehouse
	}
	st, err := tx.LockOrCreateStock(ctx, a.ProductID, a.SKU, a.WarehouseID)
	if err != nil {
		if domain.KindOf(err) != 0 {
			line.Error = err.Error()
			return line, nil
		}
		return line, err
	}
	line.Old, line.New = st.Quantity, st.Quantity
	if err := st.Adjust(a.Change); err != nil {
		line.Error = err.Error()
		return line, nil
	}
	saved, err := tx.SaveStock(ctx, st)
	if err != nil {
		return line, err
	}
	if err := tx.RecordMovement(ctx, domain.Movement{StockID: st.ID, ProductID: a.ProductID, WarehouseID: a.WarehouseID,
		Kind: "ADJUST:" + string(typ), Change: a.Change, ReferenceID: ref, Reason: reason, Actor: actor}); err != nil {
		return line, err
	}
	events := []domain.Event{domain.StockLevelChanged{Stock: saved, Reason: string(typ)}}
	if saved.BelowReorderPoint() {
		events = append(events, domain.LowStockDetected{ProductID: saved.ProductID, Available: saved.Available(), ReorderPoint: saved.ReorderPoint})
	}
	if err := tx.Publish(ctx, events...); err != nil {
		return line, err
	}
	line.New, line.Success = saved.Quantity, true
	return line, nil
}

// UpsertStockInput: tạo/cập nhật dòng tồn kho với giá trị tuyệt đối (API cũ POST/PUT /inventory).
type UpsertStockInput struct {
	ProductID       string `json:"productId"`
	SKU             string `json:"sku"`
	WarehouseID     string `json:"warehouseId"`
	Quantity        *int32 `json:"stock"`
	ReorderPoint    *int32 `json:"reorderPoint"`
	ReorderQuantity *int32 `json:"reorderQuantity"`
	InTransit       *int32 `json:"inTransit"`
	Discontinued    *bool  `json:"discontinued"`
}

func (s *Service) UpsertStock(ctx context.Context, in UpsertStockInput, actor string) (domain.Stock, error) {
	if in.ProductID == "" && in.SKU == "" {
		return domain.Stock{}, domain.Invalid("productId or sku is required")
	}
	if in.ProductID == "" {
		in.ProductID = in.SKU // bản Java định danh tồn kho theo SKU
	}
	if in.WarehouseID == "" {
		in.WarehouseID = s.opt.DefaultWarehouse
	}
	for name, v := range map[string]*int32{"reorderPoint": in.ReorderPoint, "reorderQuantity": in.ReorderQuantity, "inTransit": in.InTransit} {
		if v != nil && *v < 0 {
			return domain.Stock{}, domain.Invalid("%s must be >= 0", name)
		}
	}
	var out domain.Stock
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		st, err := tx.LockOrCreateStock(ctx, in.ProductID, in.SKU, in.WarehouseID)
		if err != nil {
			return err
		}
		old := st.Quantity
		if in.Quantity != nil {
			if err := st.SetQuantity(*in.Quantity); err != nil {
				return err
			}
		}
		if in.ReorderPoint != nil {
			st.ReorderPoint = *in.ReorderPoint
		}
		if in.ReorderQuantity != nil {
			st.ReorderQuantity = *in.ReorderQuantity
		}
		if in.InTransit != nil {
			st.InTransit = *in.InTransit
		}
		if in.Discontinued != nil {
			st.Discontinued = *in.Discontinued
		}
		out, err = tx.SaveStock(ctx, st)
		if err != nil {
			return err
		}
		if d := out.Quantity - old; d != 0 {
			if err := tx.RecordMovement(ctx, domain.Movement{StockID: st.ID, ProductID: st.ProductID, WarehouseID: st.WarehouseID,
				Kind: "SET", Change: d, Reason: "manual update", Actor: actor}); err != nil {
				return err
			}
		}
		return tx.Publish(ctx, domain.StockLevelChanged{Stock: out, Reason: "SET"})
	})
	return out, err
}

// DeleteBySKU xoá tồn kho của SKU; từ chối nếu còn hàng đang giữ chỗ.
// Dòng còn lịch sử reservation trỏ tới thì chuyển sang ngừng kinh doanh thay vì xoá.
func (s *Service) DeleteBySKU(ctx context.Context, sku string) error {
	return s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		stocks, err := tx.LockStocksBySKU(ctx, sku)
		if err != nil {
			return err
		}
		if len(stocks) == 0 {
			return domain.NotFound("Inventory item not found for SKU: %s", sku)
		}
		for _, st := range stocks {
			if st.Reserved > 0 {
				return domain.Conflict("inventory has active reservations")
			}
		}
		left, err := tx.DeleteUnreferencedStocks(ctx, sku)
		if err != nil || left == 0 {
			return err
		}
		rest, err := tx.LockStocksBySKU(ctx, sku)
		if err != nil {
			return err
		}
		for _, st := range rest {
			st.Discontinued = true
			if _, err := tx.SaveStock(ctx, st); err != nil {
				return err
			}
		}
		return nil
	})
}
