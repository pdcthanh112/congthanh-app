package postgres

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

// Warehouses hiện thực app.WarehouseRepository.
type Warehouses struct{ pool *pgxpool.Pool }

const whCols = `id, name, location, address, is_active, created_at, updated_at`

func scanWarehouse(r pgx.CollectableRow) (domain.Warehouse, error) {
	var w domain.Warehouse
	return w, r.Scan(&w.ID, &w.Name, &w.Location, &w.Address, &w.Active, &w.CreatedAt, &w.UpdatedAt)
}

func (w Warehouses) one(ctx context.Context, sql string, args ...any) (domain.Warehouse, error) {
	rows, err := w.pool.Query(ctx, sql, args...)
	if err != nil {
		return domain.Warehouse{}, err
	}
	return pgx.CollectExactlyOneRow(rows, scanWarehouse)
}

func (w Warehouses) List(ctx context.Context) ([]domain.Warehouse, error) {
	rows, err := w.pool.Query(ctx, `SELECT `+whCols+` FROM warehouses ORDER BY id`)
	if err != nil {
		return nil, err
	}
	return pgx.CollectRows(rows, scanWarehouse)
}

func (w Warehouses) Get(ctx context.Context, id string) (domain.Warehouse, error) {
	wh, err := w.one(ctx, `SELECT `+whCols+` FROM warehouses WHERE id = $1`, id)
	if errors.Is(err, pgx.ErrNoRows) {
		return wh, domain.NotFound("warehouse not found: %s", id)
	}
	return wh, err
}

func (w Warehouses) Create(ctx context.Context, in domain.Warehouse) (domain.Warehouse, error) {
	wh, err := w.one(ctx, `INSERT INTO warehouses (id, name, location, address, is_active) VALUES ($1, $2, $3, $4, $5)
		RETURNING `+whCols, in.ID, in.Name, in.Location, in.Address, in.Active)
	if pgCode(err) == "23505" {
		return wh, domain.Conflict("warehouse already exists: %s", in.ID)
	}
	return wh, err
}

func (w Warehouses) Update(ctx context.Context, in domain.Warehouse) (domain.Warehouse, error) {
	wh, err := w.one(ctx, `UPDATE warehouses SET name = $2, location = $3, address = $4, is_active = $5, updated_at = now()
		WHERE id = $1 RETURNING `+whCols, in.ID, in.Name, in.Location, in.Address, in.Active)
	if errors.Is(err, pgx.ErrNoRows) {
		return wh, domain.NotFound("warehouse not found: %s", in.ID)
	}
	return wh, err
}

// Delete chỉ xoá được kho không còn tồn kho; ngược lại hãy chuyển isActive=false.
func (w Warehouses) Delete(ctx context.Context, id string) error {
	tag, err := w.pool.Exec(ctx, `DELETE FROM warehouses WHERE id = $1`, id)
	if pgCode(err) == "23503" {
		return domain.Conflict("warehouse still has stock; deactivate it instead")
	}
	if err != nil {
		return err
	}
	if tag.RowsAffected() == 0 {
		return domain.NotFound("warehouse not found: %s", id)
	}
	return nil
}
