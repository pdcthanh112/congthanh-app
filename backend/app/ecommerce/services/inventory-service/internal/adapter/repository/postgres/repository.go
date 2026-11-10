// Package postgres hiện thực các port lưu trữ của inventory-service bằng PostgreSQL (pgx).
package postgres

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

type Repository struct {
	pool *pgxpool.Pool
	ids  *idgen.Generator
}

func New(pool *pgxpool.Pool, ids *idgen.Generator) *Repository {
	return &Repository{pool: pool, ids: ids}
}

var (
	_ app.UnitOfWork          = (*Repository)(nil)
	_ app.Reader              = (*Repository)(nil)
	_ app.WarehouseRepository = Warehouses{}
)

func (r *Repository) Do(ctx context.Context, fn func(ctx context.Context, tx app.Tx) error) error {
	t, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = t.Rollback(ctx) }()
	if err := fn(ctx, &tx{t: t, ids: r.ids}); err != nil {
		return err
	}
	return t.Commit(ctx)
}

func (r *Repository) Warehouses() Warehouses { return Warehouses{r.pool} }

const stockCols = `s.id, s.product_id, s.sku, s.warehouse_id, w.name, w.location, s.quantity, s.reserved,
	s.in_transit, s.reorder_point, s.reorder_quantity, s.discontinued, s.updated_at`

const stockFrom = ` FROM stocks s JOIN warehouses w ON w.id = s.warehouse_id`

func scanStock(row pgx.CollectableRow) (domain.Stock, error) {
	var s domain.Stock
	err := row.Scan(&s.ID, &s.ProductID, &s.SKU, &s.WarehouseID, &s.WarehouseName, &s.Location, &s.Quantity,
		&s.Reserved, &s.InTransit, &s.ReorderPoint, &s.ReorderQuantity, &s.Discontinued, &s.UpdatedAt)
	return s, err
}

type querier interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

func stocks(ctx context.Context, q querier, sql string, args ...any) ([]domain.Stock, error) {
	rows, err := q.Query(ctx, `SELECT `+stockCols+stockFrom+` `+sql, args...)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, scanStock)
}

func (r *Repository) StocksByProduct(ctx context.Context, productIDs []string, warehouseID string) ([]domain.Stock, error) {
	if warehouseID != "" {
		return stocks(ctx, r.pool, `WHERE s.product_id = ANY($1) AND s.warehouse_id = $2 ORDER BY s.product_id, s.warehouse_id`, productIDs, warehouseID)
	}
	return stocks(ctx, r.pool, `WHERE s.product_id = ANY($1) ORDER BY s.product_id, s.warehouse_id`, productIDs)
}

func (r *Repository) StocksBySKU(ctx context.Context, sku string) ([]domain.Stock, error) {
	return stocks(ctx, r.pool, `WHERE s.sku = $1 ORDER BY s.warehouse_id`, sku)
}

func (r *Repository) ExpiredReservations(ctx context.Context, limit int) ([]string, error) {
	rows, err := r.pool.Query(ctx, `SELECT id FROM reservations WHERE status = 'RESERVED' AND expires_at < now() ORDER BY expires_at LIMIT $1`, limit)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, pgx.RowTo[string])
}

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

func pgCode(err error) string {
	var pe *pgconn.PgError
	if errors.As(err, &pe) {
		return pe.Code
	}
	return ""
}

// tx hiện thực app.Tx trên một pgx.Tx.
type tx struct {
	t   pgx.Tx
	ids *idgen.Generator
}

func (x *tx) LockReservation(ctx context.Context, id string) (domain.Reservation, error) {
	var r domain.Reservation
	err := x.t.QueryRow(ctx, `SELECT id, customer_id, order_id, type, status, reason, expires_at
		FROM reservations WHERE id = $1 FOR UPDATE`, id).
		Scan(&r.ID, &r.CustomerID, &r.OrderID, &r.Type, &r.Status, &r.Reason, &r.ExpiresAt)
	if errors.Is(err, pgx.ErrNoRows) {
		return r, domain.NotFound("reservation not found: %s", id)
	}
	return r, err
}

func (x *tx) CreateReservation(ctx context.Context, r domain.Reservation) error {
	_, err := x.t.Exec(ctx, `INSERT INTO reservations (id, customer_id, order_id, type, status, expires_at)
		VALUES ($1, $2, $3, $4, $5, $6)`, r.ID, r.CustomerID, r.OrderID, r.Type, r.Status, r.ExpiresAt)
	return err
}

func (x *tx) SaveReservation(ctx context.Context, r domain.Reservation) error {
	_, err := x.t.Exec(ctx, `UPDATE reservations SET status = $2, order_id = $3, reason = $4, updated_at = now() WHERE id = $1`,
		r.ID, r.Status, r.OrderID, r.Reason)
	return err
}

func (x *tx) AddReservationItems(ctx context.Context, id string, items []domain.ReservationItem) error {
	rows := make([][]any, len(items))
	for i, it := range items {
		rows[i] = []any{id, it.StockID, it.ProductID, it.WarehouseID, it.Quantity}
	}
	_, err := x.t.CopyFrom(ctx, pgx.Identifier{"reservation_items"},
		[]string{"reservation_id", "stock_id", "product_id", "warehouse_id", "quantity"}, pgx.CopyFromRows(rows))
	return err
}

func (x *tx) LockActiveReservationItems(ctx context.Context, id string, productIDs []string) ([]domain.ReservationItem, error) {
	q := `SELECT stock_id, product_id, warehouse_id, quantity FROM reservation_items
		WHERE reservation_id = $1 AND status = 'RESERVED'`
	args := []any{id}
	if len(productIDs) > 0 {
		q += ` AND product_id = ANY($2)`
		args = append(args, productIDs)
	}
	rows, err := x.t.Query(ctx, q+` ORDER BY product_id, stock_id FOR UPDATE`, args...)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, func(r pgx.CollectableRow) (domain.ReservationItem, error) {
		var it domain.ReservationItem
		return it, r.Scan(&it.StockID, &it.ProductID, &it.WarehouseID, &it.Quantity)
	})
}

func (x *tx) SetReservationItemsStatus(ctx context.Context, id string, stockIDs []int64, status string) error {
	if len(stockIDs) == 0 {
		return nil
	}
	_, err := x.t.Exec(ctx, `UPDATE reservation_items SET status = $3 WHERE reservation_id = $1 AND stock_id = ANY($2)`, id, stockIDs, status)
	return err
}

func (x *tx) CountActiveReservationItems(ctx context.Context, id string) (int, error) {
	var n int
	err := x.t.QueryRow(ctx, `SELECT count(*) FROM reservation_items WHERE reservation_id = $1 AND status = 'RESERVED'`, id).Scan(&n)
	return n, err
}

// LockSellableStocks khoá các dòng tồn kho có thể bán của sản phẩm (kho đang hoạt động).
func (x *tx) LockSellableStocks(ctx context.Context, productID string) ([]domain.Stock, error) {
	return stocks(ctx, x.t, `WHERE s.product_id = $1 AND NOT s.discontinued AND w.is_active ORDER BY s.id FOR UPDATE OF s`, productID)
}

func (x *tx) LockStocks(ctx context.Context, ids []int64) (map[int64]domain.Stock, error) {
	list, err := stocks(ctx, x.t, `WHERE s.id = ANY($1) ORDER BY s.id FOR UPDATE OF s`, ids)
	if err != nil {
		return nil, err
	}
	m := make(map[int64]domain.Stock, len(list))
	for _, s := range list {
		m[s.ID] = s
	}
	return m, nil
}

func (x *tx) LockOrCreateStock(ctx context.Context, productID, sku, warehouseID string) (domain.Stock, error) {
	_, err := x.t.Exec(ctx, `INSERT INTO stocks (id, product_id, sku, warehouse_id) VALUES ($1, $2, $3, $4)
		ON CONFLICT (product_id, warehouse_id) DO NOTHING`, x.ids.Next(), productID, sku, warehouseID)
	if pgCode(err) == "23503" {
		return domain.Stock{}, domain.Invalid("warehouse not found: %s", warehouseID)
	}
	if err != nil {
		return domain.Stock{}, err
	}
	if sku != "" {
		if _, err := x.t.Exec(ctx, `UPDATE stocks SET sku = $3 WHERE product_id = $1 AND warehouse_id = $2 AND sku = ''`, productID, warehouseID, sku); err != nil {
			return domain.Stock{}, err
		}
	}
	list, err := stocks(ctx, x.t, `WHERE s.product_id = $1 AND s.warehouse_id = $2 FOR UPDATE OF s`, productID, warehouseID)
	if err != nil {
		return domain.Stock{}, err
	}
	return list[0], nil
}

func (x *tx) LockStocksBySKU(ctx context.Context, sku string) ([]domain.Stock, error) {
	return stocks(ctx, x.t, `WHERE s.sku = $1 ORDER BY s.id FOR UPDATE OF s`, sku)
}

func (x *tx) SaveStock(ctx context.Context, s domain.Stock) (domain.Stock, error) {
	err := x.t.QueryRow(ctx, `UPDATE stocks SET quantity = $2, reserved = $3, in_transit = $4, reorder_point = $5,
		reorder_quantity = $6, discontinued = $7, updated_at = now() WHERE id = $1 RETURNING updated_at`,
		s.ID, s.Quantity, s.Reserved, s.InTransit, s.ReorderPoint, s.ReorderQuantity, s.Discontinued).Scan(&s.UpdatedAt)
	if pgCode(err) == "23514" { // check constraint: lớp DB chặn thêm một lần nữa
		return s, domain.Conflict("stock invariant violated for %s at %s", s.ProductID, s.WarehouseID)
	}
	return s, err
}

func (x *tx) DeleteUnreferencedStocks(ctx context.Context, sku string) (int, error) {
	if _, err := x.t.Exec(ctx, `DELETE FROM stocks s WHERE s.sku = $1
		AND NOT EXISTS (SELECT 1 FROM reservation_items ri WHERE ri.stock_id = s.id)`, sku); err != nil {
		return 0, err
	}
	var left int
	err := x.t.QueryRow(ctx, `SELECT count(*) FROM stocks WHERE sku = $1`, sku).Scan(&left)
	return left, err
}

func (x *tx) RecordMovement(ctx context.Context, m domain.Movement) error {
	_, err := x.t.Exec(ctx, `INSERT INTO movements (stock_id, product_id, warehouse_id, kind, change, reference_id, reason, actor)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`, m.StockID, m.ProductID, m.WarehouseID, m.Kind, m.Change, m.ReferenceID, m.Reason, m.Actor)
	return err
}

func (x *tx) Publish(ctx context.Context, events ...domain.Event) error {
	msgs := make([]outbox.Message, 0, len(events))
	for _, e := range events {
		m, err := messaging.ToOutbox(e)
		if err != nil {
			return err
		}
		msgs = append(msgs, m)
	}
	return outbox.Write(ctx, x.t, messaging.Source, msgs...)
}
