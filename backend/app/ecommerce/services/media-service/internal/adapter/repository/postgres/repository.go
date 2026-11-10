// Package postgres là driven adapter Repository của media-service.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/media-service/internal/domain"
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

// UUIDv7: có thứ tự thời gian (index B-tree gọn) và không đoán được như số tăng dần.
type UUIDv7 struct{}

func (UUIDv7) NewID() string { return uuid.Must(uuid.NewV7()).String() }

const cols = `id, owner_id, purpose, object_key, file_name, content_type, size_bytes, status, created_at, completed_at`

func scan(row pgx.CollectableRow) (domain.Media, error) {
	var m domain.Media
	err := row.Scan(&m.ID, &m.OwnerID, &m.Purpose, &m.ObjectKey, &m.FileName, &m.ContentType, &m.SizeBytes, &m.Status, &m.CreatedAt, &m.CompletedAt)
	return m, err
}

func (r *Repository) Create(ctx context.Context, m domain.Media) error {
	_, err := r.pool.Exec(ctx, `INSERT INTO media (`+cols+`) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
		m.ID, m.OwnerID, m.Purpose, m.ObjectKey, m.FileName, m.ContentType, m.SizeBytes, m.Status, m.CreatedAt, m.CompletedAt)
	return err
}

func (r *Repository) Get(ctx context.Context, id string) (domain.Media, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+cols+` FROM media WHERE id = $1`, id)
	m, err := pgx.CollectExactlyOneRow(rows, scan)
	if errors.Is(err, pgx.ErrNoRows) {
		return m, apperr.NotFound("media %s not found", id)
	}
	return m, err
}

func (r *Repository) MarkReady(ctx context.Context, id string, at time.Time) error {
	_, err := r.pool.Exec(ctx, `UPDATE media SET status = 'READY', completed_at = $2 WHERE id = $1`, id, at)
	return err
}

func (r *Repository) Delete(ctx context.Context, id string) error {
	_, err := r.pool.Exec(ctx, `DELETE FROM media WHERE id = $1`, id)
	return err
}

func (r *Repository) ListByOwner(ctx context.Context, owner string, limit int) ([]domain.Media, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+cols+` FROM media WHERE owner_id = $1 AND status = 'READY'
		ORDER BY created_at DESC LIMIT $2`, owner, limit)
	return pgx.CollectRows(rows, scan)
}

func (r *Repository) StalePending(ctx context.Context, before time.Time, limit int) ([]domain.Media, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+cols+` FROM media WHERE status = 'PENDING' AND created_at < $1
		ORDER BY created_at LIMIT $2`, before, limit)
	return pgx.CollectRows(rows, scan)
}
