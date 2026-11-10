// Package postgres là driven adapter Repository của webhook-service.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/webhook-service/internal/domain"
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

type UUIDv7 struct{}

func (UUIDv7) NewID() string { return uuid.Must(uuid.NewV7()).String() }

const hookCols = `id, name, url, secret, events, active, created_at`

func scanHook(row pgx.CollectableRow) (domain.Webhook, error) {
	var w domain.Webhook
	err := row.Scan(&w.ID, &w.Name, &w.URL, &w.Secret, &w.Events, &w.Active, &w.CreatedAt)
	return w, err
}

func (r *Repository) Create(ctx context.Context, w domain.Webhook) error {
	_, err := r.pool.Exec(ctx, `INSERT INTO webhooks (`+hookCols+`) VALUES ($1,$2,$3,$4,$5,$6,$7)`,
		w.ID, w.Name, w.URL, w.Secret, w.Events, w.Active, w.CreatedAt)
	return err
}

func (r *Repository) Get(ctx context.Context, id string) (domain.Webhook, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+hookCols+` FROM webhooks WHERE id = $1`, id)
	w, err := pgx.CollectExactlyOneRow(rows, scanHook)
	if errors.Is(err, pgx.ErrNoRows) {
		return w, apperr.NotFound("webhook %s not found", id)
	}
	return w, err
}

func (r *Repository) List(ctx context.Context) ([]domain.Webhook, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+hookCols+` FROM webhooks ORDER BY created_at`)
	return pgx.CollectRows(rows, scanHook)
}

func (r *Repository) Update(ctx context.Context, w domain.Webhook) error {
	tag, err := r.pool.Exec(ctx, `UPDATE webhooks SET name=$2, url=$3, secret=$4, events=$5, active=$6, updated_at=now() WHERE id=$1`,
		w.ID, w.Name, w.URL, w.Secret, w.Events, w.Active)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("webhook %s not found", w.ID)
	}
	return err
}

func (r *Repository) Delete(ctx context.Context, id string) error {
	tag, err := r.pool.Exec(ctx, `DELETE FROM webhooks WHERE id = $1`, id)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("webhook %s not found", id)
	}
	return err
}

func (r *Repository) SubscribersTx(ctx context.Context, tx pgx.Tx, event string) ([]domain.Webhook, error) {
	rows, _ := tx.Query(ctx, `SELECT `+hookCols+` FROM webhooks WHERE active AND events @> ARRAY[$1]::text[]`, event)
	return pgx.CollectRows(rows, scanHook)
}

const enqueueSQL = `INSERT INTO deliveries (id, webhook_id, event_id, event_type, payload, status, next_attempt_at, created_at)
	VALUES ($1,$2,$3,$4,$5,$6,$7,$8) ON CONFLICT (webhook_id, event_id) DO NOTHING`

func (r *Repository) EnqueueTx(ctx context.Context, tx pgx.Tx, d domain.Delivery) error {
	_, err := tx.Exec(ctx, enqueueSQL, d.ID, d.WebhookID, d.EventID, d.EventType, d.Payload, d.Status, d.NextAttemptAt, d.CreatedAt)
	return err
}

func (r *Repository) Enqueue(ctx context.Context, d domain.Delivery) error {
	_, err := r.pool.Exec(ctx, enqueueSQL, d.ID, d.WebhookID, d.EventID, d.EventType, d.Payload, d.Status, d.NextAttemptAt, d.CreatedAt)
	return err
}

const delCols = `id, webhook_id, event_id, event_type, payload, status, attempts, next_attempt_at, last_status_code, last_error, created_at, delivered_at`

func scanDelivery(row pgx.CollectableRow) (domain.Delivery, error) {
	var d domain.Delivery
	err := row.Scan(&d.ID, &d.WebhookID, &d.EventID, &d.EventType, &d.Payload, &d.Status, &d.Attempts, &d.NextAttemptAt,
		&d.LastStatusCode, &d.LastError, &d.CreatedAt, &d.DeliveredAt)
	return d, err
}

func (r *Repository) ClaimDue(ctx context.Context, limit int, lease time.Duration) ([]domain.Delivery, error) {
	rows, _ := r.pool.Query(ctx, `UPDATE deliveries SET next_attempt_at = now() + $2::interval
		WHERE id IN (SELECT id FROM deliveries WHERE status = 'PENDING' AND next_attempt_at <= now()
		             ORDER BY next_attempt_at LIMIT $1 FOR UPDATE SKIP LOCKED)
		RETURNING `+delCols, limit, lease.String())
	return pgx.CollectRows(rows, scanDelivery)
}

func (r *Repository) SaveAttempt(ctx context.Context, d domain.Delivery) error {
	_, err := r.pool.Exec(ctx, `UPDATE deliveries SET status=$2, attempts=$3, next_attempt_at=$4, last_status_code=$5,
		last_error=$6, delivered_at=$7 WHERE id=$1`, d.ID, d.Status, d.Attempts, d.NextAttemptAt, d.LastStatusCode, d.LastError, d.DeliveredAt)
	return err
}

func (r *Repository) Deliveries(ctx context.Context, webhookID string, limit int) ([]domain.Delivery, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+delCols+` FROM deliveries WHERE webhook_id = $1 ORDER BY created_at DESC LIMIT $2`, webhookID, limit)
	return pgx.CollectRows(rows, scanDelivery)
}

func (r *Repository) GetDelivery(ctx context.Context, id string) (domain.Delivery, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+delCols+` FROM deliveries WHERE id = $1`, id)
	d, err := pgx.CollectExactlyOneRow(rows, scanDelivery)
	if errors.Is(err, pgx.ErrNoRows) {
		return d, apperr.NotFound("delivery %s not found", id)
	}
	return d, err
}

// Retry đưa lượt giao (kể cả DEAD) về PENDING, gửi ngay, đặt lại số lần thử.
func (r *Repository) Retry(ctx context.Context, id string) error {
	_, err := r.pool.Exec(ctx, `UPDATE deliveries SET status='PENDING', attempts=0, next_attempt_at=now() WHERE id=$1`, id)
	return err
}
