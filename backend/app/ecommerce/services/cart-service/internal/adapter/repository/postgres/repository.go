// Package postgres hiện thực Reader/UnitOfWork/Tx của cart-service bằng PostgreSQL.
package postgres

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/idgen"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/adapter/messaging"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/cart-service/internal/domain"
)

type Repository struct {
	pool *pgxpool.Pool
	ids  *idgen.Generator
}

func New(pool *pgxpool.Pool, ids *idgen.Generator) *Repository {
	return &Repository{pool: pool, ids: ids}
}

var (
	_ app.Reader     = (*Repository)(nil)
	_ app.UnitOfWork = (*Repository)(nil)
	_ app.Tx         = (*tx)(nil)
)

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

func (r *Repository) Do(ctx context.Context, fn func(ctx context.Context, t app.Tx) error) error {
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

const (
	cartCols = `id, name, customer_id, status, is_default, created_at, updated_at`
	itemCols = `id, cart_id, product_id, quantity, created_at, updated_at`
)

func scanCart(r pgx.CollectableRow) (domain.Cart, error) {
	var c domain.Cart
	return c, r.Scan(&c.ID, &c.Name, &c.CustomerID, &c.Status, &c.IsDefault, &c.CreatedAt, &c.UpdatedAt)
}

func scanItem(r pgx.CollectableRow) (domain.Item, error) {
	var i domain.Item
	return i, r.Scan(&i.ID, &i.CartID, &i.ProductID, &i.Quantity, &i.CreatedAt, &i.UpdatedAt)
}

type querier interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
}

func one[T any](ctx context.Context, q querier, scan pgx.RowToFunc[T], notFound error, sql string, args ...any) (T, error) {
	rows, err := q.Query(ctx, sql, args...)
	if err != nil {
		var zero T
		return zero, err
	}
	v, err := pgx.CollectExactlyOneRow(rows, scan)
	if errors.Is(err, pgx.ErrNoRows) {
		return v, notFound
	}
	return v, err
}

func (r *Repository) withItems(ctx context.Context, carts []domain.Cart) ([]domain.Cart, error) {
	if len(carts) == 0 {
		return []domain.Cart{}, nil
	}
	ids := make([]int64, len(carts))
	for i, c := range carts {
		ids[i] = c.ID
	}
	rows, err := r.pool.Query(ctx, `SELECT `+itemCols+` FROM cart_items WHERE cart_id = ANY($1) ORDER BY created_at, id`, ids)
	if err != nil {
		return nil, err
	}
	items, err := pgx.CollectRows(rows, scanItem)
	if err != nil {
		return nil, err
	}
	by := map[int64][]domain.Item{}
	for _, it := range items {
		by[it.CartID] = append(by[it.CartID], it)
	}
	for i := range carts {
		carts[i].Items = by[carts[i].ID]
		if carts[i].Items == nil {
			carts[i].Items = []domain.Item{}
		}
	}
	return carts, nil
}

func (r *Repository) oneCart(ctx context.Context, notFound error, sql string, args ...any) (domain.Cart, error) {
	c, err := one(ctx, r.pool, scanCart, notFound, sql, args...)
	if err != nil {
		return c, err
	}
	cs, err := r.withItems(ctx, []domain.Cart{c})
	if err != nil {
		return domain.Cart{}, err
	}
	return cs[0], nil
}

func (r *Repository) Get(ctx context.Context, id int64) (domain.Cart, error) {
	return r.oneCart(ctx, domain.NotFound("Cart NOT FOUND"), `SELECT `+cartCols+` FROM carts WHERE id = $1`, id)
}

func (r *Repository) GetDefault(ctx context.Context, customerID string) (domain.Cart, error) {
	return r.oneCart(ctx, domain.NotFound("default cart not found"),
		`SELECT `+cartCols+` FROM carts WHERE customer_id = $1 AND is_default AND status = 'ACTIVE'`, customerID)
}

// ListByCustomer: giỏ mặc định đứng đầu.
func (r *Repository) ListByCustomer(ctx context.Context, customerID string, activeOnly bool) ([]domain.Cart, error) {
	q := `SELECT ` + cartCols + ` FROM carts WHERE customer_id = $1`
	if activeOnly {
		q += ` AND status = 'ACTIVE'`
	}
	rows, err := r.pool.Query(ctx, q+` ORDER BY is_default DESC, created_at`, customerID)
	if err != nil {
		return nil, err
	}
	carts, err := pgx.CollectRows(rows, scanCart)
	if err != nil {
		return nil, err
	}
	return r.withItems(ctx, carts)
}

func (r *Repository) Item(ctx context.Context, cartID, itemID int64) (domain.Item, error) {
	return one(ctx, r.pool, scanItem, domain.NotFound("CartItem not found"),
		`SELECT `+itemCols+` FROM cart_items WHERE id = $2 AND cart_id = $1`, cartID, itemID)
}

type tx struct {
	t   pgx.Tx
	ids *idgen.Generator
}

func (x *tx) LockCustomer(ctx context.Context, customerID string) error {
	_, err := x.t.Exec(ctx, `SELECT pg_advisory_xact_lock(hashtext($1))`, customerID)
	return err
}

func (x *tx) HasDefault(ctx context.Context, customerID string) (bool, error) {
	var has bool
	err := x.t.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM carts WHERE customer_id = $1 AND is_default AND status = 'ACTIVE')`, customerID).Scan(&has)
	return has, err
}

func (x *tx) UnsetDefault(ctx context.Context, customerID string, exceptID int64) error {
	_, err := x.t.Exec(ctx, `UPDATE carts SET is_default = FALSE, updated_at = now() WHERE customer_id = $1 AND is_default AND id <> $2`, customerID, exceptID)
	return err
}

func (x *tx) InsertCart(ctx context.Context, c domain.Cart) (domain.Cart, error) {
	out, err := one(ctx, x.t, scanCart, nil, `INSERT INTO carts (id, name, customer_id, status, is_default)
		VALUES ($1, $2, $3, $4, $5) RETURNING `+cartCols, x.ids.Next(), c.Name, c.CustomerID, c.Status, c.IsDefault)
	out.Items = []domain.Item{}
	return out, err
}

func (x *tx) LockCart(ctx context.Context, id int64) (domain.Cart, error) {
	return one(ctx, x.t, scanCart, domain.NotFound("Cart NOT FOUND"), `SELECT `+cartCols+` FROM carts WHERE id = $1 FOR UPDATE`, id)
}

func (x *tx) SaveCart(ctx context.Context, c domain.Cart) (domain.Cart, error) {
	err := x.t.QueryRow(ctx, `UPDATE carts SET name = $2, status = $3, is_default = $4, updated_at = now()
		WHERE id = $1 RETURNING updated_at`, c.ID, c.Name, c.Status, c.IsDefault).Scan(&c.UpdatedAt)
	return c, err
}

func (x *tx) DeleteCart(ctx context.Context, id int64) error {
	_, err := x.t.Exec(ctx, `DELETE FROM carts WHERE id = $1`, id)
	return err
}

func (x *tx) ItemStats(ctx context.Context, cartID int64, productID string) (int, int32, error) {
	var count int
	var current int32
	err := x.t.QueryRow(ctx, `SELECT count(*), COALESCE(max(quantity) FILTER (WHERE product_id = $2), 0)
		FROM cart_items WHERE cart_id = $1`, cartID, productID).Scan(&count, &current)
	return count, current, err
}

func (x *tx) AddItem(ctx context.Context, cartID int64, productID string, qty int32) (domain.Item, error) {
	it, err := one(ctx, x.t, scanItem, nil, `INSERT INTO cart_items (id, cart_id, product_id, quantity) VALUES ($1, $2, $3, $4)
		ON CONFLICT (cart_id, product_id) DO UPDATE SET quantity = cart_items.quantity + EXCLUDED.quantity, updated_at = now()
		RETURNING `+itemCols, x.ids.Next(), cartID, productID, qty)
	if err != nil {
		return it, err
	}
	_, err = x.t.Exec(ctx, `UPDATE carts SET updated_at = now() WHERE id = $1`, cartID)
	return it, err
}

func (x *tx) SetItemQuantity(ctx context.Context, cartID, itemID int64, qty int32) (domain.Item, error) {
	return one(ctx, x.t, scanItem, domain.NotFound("cart item not found"), `UPDATE cart_items SET quantity = $3, updated_at = now()
		WHERE id = $2 AND cart_id = $1 RETURNING `+itemCols, cartID, itemID, qty)
}

func (x *tx) DeleteItem(ctx context.Context, cartID, itemID int64) (domain.Item, error) {
	return one(ctx, x.t, scanItem, domain.NotFound("cart item not found"),
		`DELETE FROM cart_items WHERE id = $2 AND cart_id = $1 RETURNING `+itemCols, cartID, itemID)
}

func (x *tx) DeleteItems(ctx context.Context, cartID int64, productIDs []string) ([]string, error) {
	q, args := `DELETE FROM cart_items WHERE cart_id = $1`, []any{cartID}
	if len(productIDs) > 0 {
		q += ` AND product_id = ANY($2)`
		args = append(args, productIDs)
	}
	rows, err := x.t.Query(ctx, q+` RETURNING product_id`, args...)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, pgx.RowTo[string])
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
