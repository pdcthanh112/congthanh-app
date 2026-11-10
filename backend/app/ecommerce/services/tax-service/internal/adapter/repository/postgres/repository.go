// Package postgres là driven adapter Repository của tax-service (pgx).
package postgres

import (
	"context"
	"errors"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/tax-service/internal/domain"
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

// mapErr: vi phạm unique → Conflict, khoá ngoại → Invalid.
func mapErr(err error, what string) error {
	var pg *pgconn.PgError
	if errors.As(err, &pg) {
		switch pg.Code {
		case "23505":
			return apperr.Conflict("%s already exists", what)
		case "23503":
			return apperr.Invalid("referenced tax class does not exist")
		}
	}
	return err
}

func (r *Repository) ListClasses(ctx context.Context) ([]domain.TaxClass, error) {
	rows, _ := r.pool.Query(ctx, `SELECT id, name FROM tax_classes ORDER BY id`)
	return pgx.CollectRows(rows, pgx.RowToStructByPos[domain.TaxClass])
}

func (r *Repository) GetClass(ctx context.Context, id int64) (domain.TaxClass, error) {
	var c domain.TaxClass
	err := r.pool.QueryRow(ctx, `SELECT id, name FROM tax_classes WHERE id = $1`, id).Scan(&c.ID, &c.Name)
	if errors.Is(err, pgx.ErrNoRows) {
		return c, apperr.NotFound("tax class %d not found", id)
	}
	return c, err
}

func (r *Repository) CreateClass(ctx context.Context, name string) (domain.TaxClass, error) {
	c := domain.TaxClass{Name: name}
	err := r.pool.QueryRow(ctx, `INSERT INTO tax_classes (name) VALUES ($1) RETURNING id`, name).Scan(&c.ID)
	return c, mapErr(err, "tax class "+name)
}

func (r *Repository) UpdateClass(ctx context.Context, c domain.TaxClass) error {
	tag, err := r.pool.Exec(ctx, `UPDATE tax_classes SET name = $2, updated_at = now() WHERE id = $1`, c.ID, c.Name)
	if err != nil {
		return mapErr(err, "tax class "+c.Name)
	}
	if tag.RowsAffected() == 0 {
		return apperr.NotFound("tax class %d not found", c.ID)
	}
	return nil
}

func (r *Repository) DeleteClass(ctx context.Context, id int64) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM tax_classes WHERE id = $1`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("tax class %d not found", id)
	}
	return err
}

const rateCols = `id, tax_class_id, country_code, region_code, postal_code, rate_percent::float8`

func (r *Repository) ListRates(ctx context.Context, page, size int) ([]domain.TaxRate, int, error) {
	var total int
	if err := r.pool.QueryRow(ctx, `SELECT count(*) FROM tax_rates`).Scan(&total); err != nil {
		return nil, 0, err
	}
	rows, _ := r.pool.Query(ctx, `SELECT `+rateCols+` FROM tax_rates
		ORDER BY country_code, tax_class_id, region_code, postal_code LIMIT $1 OFFSET $2`, size, page*size)
	list, err := pgx.CollectRows(rows, pgx.RowToStructByPos[domain.TaxRate])
	return list, total, err
}

func (r *Repository) GetRate(ctx context.Context, id int64) (domain.TaxRate, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+rateCols+` FROM tax_rates WHERE id = $1`, id)
	t, err := pgx.CollectExactlyOneRow(rows, pgx.RowToStructByPos[domain.TaxRate])
	if errors.Is(err, pgx.ErrNoRows) {
		return t, apperr.NotFound("tax rate %d not found", id)
	}
	return t, err
}

func (r *Repository) CreateRate(ctx context.Context, t domain.TaxRate) (domain.TaxRate, error) {
	err := r.pool.QueryRow(ctx, `INSERT INTO tax_rates (tax_class_id, country_code, region_code, postal_code, rate_percent)
		VALUES ($1, $2, $3, $4, $5) RETURNING id`, t.TaxClassID, t.CountryCode, t.RegionCode, t.PostalCode, t.RatePercent).Scan(&t.ID)
	return t, mapErr(err, "tax rate for this class and location")
}

func (r *Repository) UpdateRate(ctx context.Context, t domain.TaxRate) error {
	tag, err := r.pool.Exec(ctx, `UPDATE tax_rates SET tax_class_id = $2, country_code = $3, region_code = $4,
		postal_code = $5, rate_percent = $6, updated_at = now() WHERE id = $1`,
		t.ID, t.TaxClassID, t.CountryCode, t.RegionCode, t.PostalCode, t.RatePercent)
	if err != nil {
		return mapErr(err, "tax rate for this class and location")
	}
	if tag.RowsAffected() == 0 {
		return apperr.NotFound("tax rate %d not found", t.ID)
	}
	return nil
}

func (r *Repository) DeleteRate(ctx context.Context, id int64) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM tax_rates WHERE id = $1`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("tax rate %d not found", id)
	}
	return err
}

func (r *Repository) RatesFor(ctx context.Context, country string, classIDs []int64) ([]domain.TaxRate, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+rateCols+` FROM tax_rates
		WHERE country_code = $1 AND tax_class_id = ANY($2)`, country, classIDs)
	return pgx.CollectRows(rows, pgx.RowToStructByPos[domain.TaxRate])
}
