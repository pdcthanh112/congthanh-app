package app

import (
	"context"
	"errors"
	"slices"
	"sync/atomic"
	"time"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

type Options struct {
	ReservationTTL   time.Duration
	DefaultWarehouse string
	FlashClaimTTL    time.Duration // khách giành được suất phải đặt đơn trong khoảng này
}

type Service struct {
	uow        UnitOfWork
	read       Reader
	warehouses WarehouseRepository
	flash      FlashSaleStore // có thể nil (tắt flash sale)
	claims     ClaimPublisher // có thể nil
	opt        Options
	now        func() time.Time
	// Số sale đang chạy theo lần quét gần nhất; -1 = chưa biết. Xem flashClaimOf.
	activeSales atomic.Int64
}

func NewService(uow UnitOfWork, read Reader, wh WarehouseRepository, flash FlashSaleStore, claims ClaimPublisher, opt Options) *Service {
	if opt.ReservationTTL == 0 {
		opt.ReservationTTL = 15 * time.Minute
	}
	if opt.DefaultWarehouse == "" {
		opt.DefaultWarehouse = "WH-DEFAULT"
	}
	if opt.FlashClaimTTL == 0 {
		opt.FlashClaimTTL = 10 * time.Minute
	}
	s := &Service{uow: uow, read: read, warehouses: wh, flash: flash, claims: claims, opt: opt, now: time.Now}
	s.activeSales.Store(-1)
	return s
}

type ReserveInput struct {
	ID         string
	CustomerID string
	Type       domain.ReservationType
	ExpiresAt  time.Time
	Items      []domain.Item
}

// errRollback huỷ transaction nhưng vẫn trả kết quả nghiệp vụ (vd. thiếu hàng).
var errRollback = errors.New("rollback")

// Reserve giữ chỗ toàn bộ items (all-or-nothing). Gọi lại cùng ID là idempotent.
func (s *Service) Reserve(ctx context.Context, in ReserveInput) (domain.ReserveResult, error) {
	if in.ID == "" {
		return domain.ReserveResult{}, domain.Invalid("reservationId is required")
	}
	items, err := domain.MergeItems(in.Items)
	if err != nil {
		return domain.ReserveResult{}, err
	}
	if len(items) == 0 {
		return domain.ReserveResult{}, domain.Invalid("items must not be empty")
	}
	if fc, ok, err := s.flashClaimOf(ctx, in.ID); err != nil {
		return domain.ReserveResult{}, err
	} else if ok {
		return s.confirmFlashClaim(ctx, fc, in, items)
	}
	now := s.now()
	if in.ExpiresAt.IsZero() || !in.ExpiresAt.After(now) {
		in.ExpiresAt = now.Add(s.opt.ReservationTTL)
	}
	if in.Type == "" {
		in.Type = "ORDER"
	}
	res := domain.ReserveResult{ID: in.ID, ReservedAt: now, ExpiresAt: in.ExpiresAt}

	err = s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		existing, err := tx.LockReservation(ctx, in.ID)
		switch {
		case err == nil:
			if existing.Status != domain.StatusReserved {
				res.Message = "reservation already " + string(existing.Status)
				return errRollback
			}
			lines, err := s.activeLines(ctx, tx, in.ID)
			if err != nil {
				return err
			}
			res.Success, res.Lines, res.ExpiresAt = true, lines, existing.ExpiresAt
			res.Message = "reservation already exists"
			return nil
		case domain.KindOf(err) != domain.KindNotFound:
			return err
		}

		r := domain.Reservation{ID: in.ID, CustomerID: in.CustomerID, Type: in.Type, Status: domain.StatusReserved, ExpiresAt: in.ExpiresAt}
		if err := tx.CreateReservation(ctx, r); err != nil {
			return err
		}
		var allocs []domain.Allocation
		ok := true
		for _, it := range items {
			stocks, err := tx.LockSellableStocks(ctx, it.ProductID)
			if err != nil {
				return err
			}
			al := domain.Allocate(it, stocks)
			res.Lines = append(res.Lines, al.Line)
			allocs = append(allocs, al)
			ok = ok && al.Complete()
		}
		if !ok {
			res.Message = "insufficient inventory"
			return errRollback
		}
		var reserved []domain.ReservationItem
		for _, al := range allocs {
			if err := s.applyHolds(ctx, tx, in, al.Items); err != nil {
				return err
			}
			reserved = append(reserved, al.Items...)
		}
		if err := tx.AddReservationItems(ctx, in.ID, reserved); err != nil {
			return err
		}
		res.Success, res.Message = true, "inventory reserved"
		return tx.Publish(ctx, domain.InventoryReserved{ReservationID: in.ID, CustomerID: in.CustomerID, Items: reserved, ExpiresAtUnix: in.ExpiresAt.Unix()})
	})
	if errors.Is(err, errRollback) {
		err = s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
			return tx.Publish(ctx, domain.InventoryReservationFailed{ReservationID: in.ID, Reason: res.Message})
		})
	}
	return res, err
}

func (s *Service) applyHolds(ctx context.Context, tx Tx, in ReserveInput, items []domain.ReservationItem) error {
	ids := make([]int64, len(items))
	for i, it := range items {
		ids[i] = it.StockID
	}
	stocks, err := tx.LockStocks(ctx, ids)
	if err != nil {
		return err
	}
	for _, it := range items {
		st := stocks[it.StockID]
		if err := st.Reserve(it.Quantity); err != nil {
			return err
		}
		if _, err := tx.SaveStock(ctx, st); err != nil {
			return err
		}
		if err := tx.RecordMovement(ctx, domain.Movement{StockID: st.ID, ProductID: st.ProductID, WarehouseID: st.WarehouseID,
			Kind: "RESERVE", Change: it.Quantity, ReferenceID: in.ID, Actor: in.CustomerID}); err != nil {
			return err
		}
	}
	return nil
}

func (s *Service) activeLines(ctx context.Context, tx Tx, id string) ([]domain.ReserveLine, error) {
	items, err := tx.LockActiveReservationItems(ctx, id, nil)
	if err != nil {
		return nil, err
	}
	by := map[string]*domain.ReserveLine{}
	var order []string
	for _, it := range items {
		l, ok := by[it.ProductID]
		if !ok {
			l = &domain.ReserveLine{ProductID: it.ProductID, WarehouseID: it.WarehouseID}
			by[it.ProductID] = l
			order = append(order, it.ProductID)
		}
		l.Reserved += it.Quantity
		l.Requested = l.Reserved
	}
	slices.Sort(order)
	out := make([]domain.ReserveLine, 0, len(order))
	for _, p := range order {
		out = append(out, *by[p])
	}
	return out, nil
}

// Release trả hàng đã giữ. Gọi lại khi đã release/expire vẫn thành công
// (compensation của saga phải idempotent).
func (s *Service) Release(ctx context.Context, id string, productIDs []string, reason string, expired bool) (domain.ReleaseResult, error) {
	res := domain.ReleaseResult{ID: id, ReleasedAt: s.now()}
	if fc, ok, err := s.flashClaimOf(ctx, id); err != nil {
		return res, err
	} else if ok {
		return s.releaseFlashClaim(ctx, fc, res)
	}
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		r, err := tx.LockReservation(ctx, id)
		if err != nil {
			return err
		}
		if r.Status != domain.StatusReserved {
			res.Success = r.Status != domain.StatusCommitted
			res.Message = "reservation already " + string(r.Status)
			return nil
		}
		items, err := tx.LockActiveReservationItems(ctx, id, productIDs)
		if err != nil {
			return err
		}
		stocks, err := tx.LockStocks(ctx, stockIDs(items))
		if err != nil {
			return err
		}
		released := map[string]int32{}
		for _, it := range items {
			st := stocks[it.StockID]
			st.Release(it.Quantity)
			if _, err := tx.SaveStock(ctx, st); err != nil {
				return err
			}
			stocks[it.StockID] = st
			if err := tx.RecordMovement(ctx, domain.Movement{StockID: st.ID, ProductID: it.ProductID, WarehouseID: it.WarehouseID,
				Kind: "RELEASE", Change: -it.Quantity, ReferenceID: id, Reason: reason}); err != nil {
				return err
			}
			released[it.ProductID] += it.Quantity
		}
		if err := tx.SetReservationItemsStatus(ctx, id, stockIDs(items), "RELEASED"); err != nil {
			return err
		}
		for _, p := range sortedKeys(released) {
			res.Lines = append(res.Lines, domain.ReleaseLine{ProductID: p, Released: released[p]})
		}
		left, err := tx.CountActiveReservationItems(ctx, id)
		if err != nil {
			return err
		}
		if left == 0 {
			r.Status, r.Reason = domain.StatusReleased, reason
			if expired {
				r.Status = domain.StatusExpired
			}
			if err := tx.SaveReservation(ctx, r); err != nil {
				return err
			}
		}
		res.Success, res.Message = true, "inventory released"
		return tx.Publish(ctx, domain.InventoryReleased{ReservationID: id, Items: items, Reason: reason, Expired: expired})
	})
	return res, err
}

// Commit chốt reservation khi đơn đã thanh toán: trừ hẳn tồn kho. items rỗng = chốt
// toàn bộ; có items thì chốt tối đa theo items, phần còn lại trả về kho.
func (s *Service) Commit(ctx context.Context, id, orderID string, items []domain.Item) (domain.CommitResult, error) {
	res := domain.CommitResult{ID: id, CommittedAt: s.now()}
	if fc, ok, err := s.flashClaimOf(ctx, id); err != nil {
		return res, err
	} else if ok {
		return s.commitFlashClaim(ctx, fc, res)
	}
	var want map[string]int32
	if len(items) > 0 {
		merged, err := domain.MergeItems(items)
		if err != nil {
			return res, err
		}
		want = map[string]int32{}
		for _, it := range merged {
			want[it.ProductID] = it.Quantity
		}
	}
	err := s.uow.Do(ctx, func(ctx context.Context, tx Tx) error {
		r, err := tx.LockReservation(ctx, id)
		if err != nil {
			return err
		}
		if r.Status != domain.StatusReserved {
			res.Success = r.Status == domain.StatusCommitted
			res.Message = "reservation already " + string(r.Status)
			return nil
		}
		ris, err := tx.LockActiveReservationItems(ctx, id, nil)
		if err != nil {
			return err
		}
		stocks, err := tx.LockStocks(ctx, stockIDs(ris))
		if err != nil {
			return err
		}
		committed, avail := map[string]int32{}, map[string]int32{}
		var events []domain.Event
		var done []domain.ReservationItem
		for _, it := range ris {
			c := it.Quantity
			if want != nil {
				c = min(it.Quantity, want[it.ProductID])
				want[it.ProductID] -= c
			}
			st := stocks[it.StockID]
			st.Commit(c, it.Quantity)
			saved, err := tx.SaveStock(ctx, st)
			if err != nil {
				return err
			}
			stocks[it.StockID] = saved
			if c > 0 {
				if err := tx.RecordMovement(ctx, domain.Movement{StockID: st.ID, ProductID: it.ProductID, WarehouseID: it.WarehouseID,
					Kind: "COMMIT", Change: -c, ReferenceID: orderID}); err != nil {
					return err
				}
				done = append(done, domain.ReservationItem{StockID: it.StockID, ProductID: it.ProductID, WarehouseID: it.WarehouseID, Quantity: c})
				events = append(events, domain.StockLevelChanged{Stock: saved, Reason: "COMMIT"})
				if saved.BelowReorderPoint() {
					events = append(events, domain.LowStockDetected{ProductID: saved.ProductID, Available: saved.Available(), ReorderPoint: saved.ReorderPoint})
				}
			}
			committed[it.ProductID] += c
			avail[it.ProductID] += saved.Available()
		}
		if err := tx.SetReservationItemsStatus(ctx, id, stockIDs(ris), "COMMITTED"); err != nil {
			return err
		}
		for _, p := range sortedKeys(committed) {
			res.Lines = append(res.Lines, domain.CommitLine{ProductID: p, Committed: committed[p], NewAvailable: avail[p]})
		}
		r.Status, r.OrderID = domain.StatusCommitted, orderID
		if err := tx.SaveReservation(ctx, r); err != nil {
			return err
		}
		res.Success, res.Message = true, "reservation committed"
		events = append([]domain.Event{domain.InventoryCommitted{ReservationID: id, OrderID: orderID, Items: done}}, events...)
		return tx.Publish(ctx, events...)
	})
	return res, err
}

// ReleaseExpired dọn reservation quá hạn mà saga không release (chạy định kỳ).
func (s *Service) ReleaseExpired(ctx context.Context, limit int) (int, error) {
	ids, err := s.read.ExpiredReservations(ctx, limit)
	if err != nil {
		return 0, err
	}
	n := 0
	for _, id := range ids {
		if _, err := s.Release(ctx, id, nil, "reservation expired", true); err != nil {
			return n, err
		}
		n++
	}
	return n, nil
}

func stockIDs(items []domain.ReservationItem) []int64 {
	ids := make([]int64, 0, len(items))
	for _, it := range items {
		ids = append(ids, it.StockID)
	}
	return ids
}

func sortedKeys[V any](m map[string]V) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	slices.Sort(keys)
	return keys
}
