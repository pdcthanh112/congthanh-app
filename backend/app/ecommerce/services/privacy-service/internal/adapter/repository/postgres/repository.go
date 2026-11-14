// Package postgres hiện thực app.Store bằng pgx; mọi ghi đi kèm outbox trong cùng transaction.
package postgres

import (
	"context"
	"errors"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/protobuf/proto"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	privacyv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/privacy/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/privacy-service/internal/domain"
)

const (
	Source        = "privacy-service"
	AggregateType = "privacy" // → topic ecommerce.privacy.events
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

type querier interface {
	Query(ctx context.Context, sql string, args ...any) (pgx.Rows, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

func (r *Repository) InTx(ctx context.Context, fn func(app.Tx) error) error {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()
	if err := fn(&txRepo{tx: tx}); err != nil {
		return err
	}
	return tx.Commit(ctx)
}

func (r *Repository) LatestConsents(ctx context.Context, subject string) (map[domain.Purpose]domain.Consent, error) {
	rows, err := r.pool.Query(ctx, `SELECT DISTINCT ON (purpose) subject_id, purpose, granted, source, policy_version, recorded_at
		FROM consents WHERE subject_id = $1 ORDER BY purpose, recorded_at DESC, id DESC`, subject)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[domain.Purpose]domain.Consent{}
	for rows.Next() {
		var c domain.Consent
		if err := rows.Scan(&c.SubjectID, &c.Purpose, &c.Granted, &c.Source, &c.PolicyVersion, &c.RecordedAt); err != nil {
			return nil, err
		}
		out[c.Purpose] = c
	}
	return out, rows.Err()
}

func (r *Repository) GetRequest(ctx context.Context, id string) (*domain.Request, error) {
	return getRequest(ctx, r.pool, id, false)
}

func (r *Repository) RequestsBySubject(ctx context.Context, subject string, limit int) ([]domain.Request, error) {
	return listRequests(ctx, r.pool, `WHERE subject_id = $1 ORDER BY created_at DESC LIMIT $2`, subject, limit)
}

func (r *Repository) RequestsByStatus(ctx context.Context, status domain.RequestStatus, limit int) ([]domain.Request, error) {
	return listRequests(ctx, r.pool, `WHERE status = $1 ORDER BY due_at LIMIT $2`, string(status), limit)
}

func (r *Repository) StaleOpenRequests(ctx context.Context, olderThan time.Time, limit int) ([]domain.Request, error) {
	return listRequests(ctx, r.pool, `WHERE status = 'IN_PROGRESS' AND COALESCE(last_reminded_at, created_at) < $1
		ORDER BY due_at LIMIT $2`, olderThan, limit)
}

type txRepo struct{ tx pgx.Tx }

func (t *txRepo) AppendConsent(ctx context.Context, c domain.Consent) error {
	_, err := t.tx.Exec(ctx, `INSERT INTO consents (subject_id, purpose, granted, source, policy_version, recorded_at)
		VALUES ($1, $2, $3, $4, $5, $6)`, c.SubjectID, string(c.Purpose), c.Granted, string(c.Source), c.PolicyVersion, c.RecordedAt)
	return err
}

func (t *txRepo) OpenRequestOfType(ctx context.Context, subject string, typ domain.RequestType) (bool, error) {
	var exists bool
	err := t.tx.QueryRow(ctx, `SELECT EXISTS (SELECT 1 FROM dsr_requests WHERE subject_id = $1 AND type = $2 AND status = 'IN_PROGRESS')`,
		subject, string(typ)).Scan(&exists)
	return exists, err
}

func (t *txRepo) InsertRequest(ctx context.Context, r *domain.Request) error {
	_, err := t.tx.Exec(ctx, `INSERT INTO dsr_requests (id, subject_id, type, status, email_sha256, due_at, created_at, completed_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8)`, r.ID, r.SubjectID, string(r.Type), string(r.Status), r.EmailSHA256,
		r.DueAt, r.CreatedAt, r.CompletedAt)
	var pgErr *pgconn.PgError
	if errors.As(err, &pgErr) && pgErr.Code == "23505" {
		return apperr.Conflict("a %s request is already in progress", r.Type)
	}
	if err != nil {
		return err
	}
	for _, task := range r.Tasks {
		if _, err := t.tx.Exec(ctx, `INSERT INTO dsr_tasks (request_id, processor, status) VALUES ($1, $2, $3)`,
			r.ID, task.Processor, string(task.Status)); err != nil {
			return err
		}
	}
	r.Version = 1
	return nil
}

func (t *txRepo) LockRequest(ctx context.Context, id string) (*domain.Request, error) {
	return getRequest(ctx, t.tx, id, true)
}

func (t *txRepo) UpdateRequest(ctx context.Context, r *domain.Request) error {
	tag, err := t.tx.Exec(ctx, `UPDATE dsr_requests SET status = $2, due_at = $3, extended = $4, extension_reason = $5,
		completed_at = $6, version = version + 1 WHERE id = $1 AND version = $7`,
		r.ID, string(r.Status), r.DueAt, r.Extended, r.ExtensionReason, r.CompletedAt, r.Version)
	if err != nil {
		return err
	}
	if tag.RowsAffected() != 1 {
		return apperr.Conflict("request %s was modified concurrently", r.ID)
	}
	for _, task := range r.Tasks {
		if _, err := t.tx.Exec(ctx, `UPDATE dsr_tasks SET status = $3, retention_reason = $4, object_key = $5, completed_at = $6
			WHERE request_id = $1 AND processor = $2`, r.ID, task.Processor, string(task.Status), task.RetentionReason,
			task.ObjectKey, task.CompletedAt); err != nil {
			return err
		}
	}
	r.Version++
	return nil
}

func (t *txRepo) MarkReminded(ctx context.Context, id string, at time.Time) error {
	_, err := t.tx.Exec(ctx, `UPDATE dsr_requests SET last_reminded_at = $2 WHERE id = $1`, id, at)
	return err
}

func (t *txRepo) Emit(ctx context.Context, events ...domain.Event) error {
	msgs := make([]outbox.Message, 0, len(events))
	for _, e := range events {
		payload, err := toProto(e)
		if err != nil {
			return err
		}
		msgs = append(msgs, outbox.Message{AggregateType: AggregateType, AggregateID: domain.SubjectOf(e), Payload: payload})
	}
	return outbox.Write(ctx, t.tx, Source, msgs...)
}

func toProto(e domain.Event) (proto.Message, error) {
	switch ev := e.(type) {
	case domain.ConsentChanged:
		c := ev.Consent
		return &privacyv1.ConsentChanged{SubjectId: c.SubjectID, Purpose: string(c.Purpose), Granted: c.Granted,
			Source: string(c.Source), PolicyVersion: c.PolicyVersion}, nil
	case domain.RequestOpened:
		return &privacyv1.DataSubjectRequestOpened{RequestId: ev.Request.ID, SubjectId: ev.Request.SubjectID,
			Type: string(ev.Request.Type), DueAt: ev.Request.DueAt.UTC().Format(time.RFC3339)}, nil
	case domain.ErasureRequested:
		return &privacyv1.ErasureRequested{RequestId: ev.Request.ID, SubjectId: ev.Request.SubjectID, EmailSha256: ev.Request.EmailSHA256}, nil
	case domain.AccessExportRequested:
		return &privacyv1.AccessExportRequested{RequestId: ev.Request.ID, SubjectId: ev.Request.SubjectID}, nil
	case domain.RequestCompleted:
		return &privacyv1.DataSubjectRequestCompleted{RequestId: ev.Request.ID, SubjectId: ev.Request.SubjectID,
			Type: string(ev.Request.Type), Outcome: string(ev.Request.Status)}, nil
	}
	return nil, fmt.Errorf("unknown privacy event %T", e)
}

const requestColumns = `id, subject_id, type, status, email_sha256, due_at, extended, extension_reason, created_at, completed_at, version`

func getRequest(ctx context.Context, q querier, id string, lock bool) (*domain.Request, error) {
	if _, err := uuid.Parse(id); err != nil {
		return nil, apperr.NotFound("request %s not found", id)
	}
	sql := `SELECT ` + requestColumns + ` FROM dsr_requests WHERE id = $1`
	if lock {
		sql += ` FOR UPDATE`
	}
	r, err := scanRequest(q.QueryRow(ctx, sql, id))
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, apperr.NotFound("request %s not found", id)
	}
	if err != nil {
		return nil, err
	}
	if err := loadTasks(ctx, q, r); err != nil {
		return nil, err
	}
	return r, nil
}

func listRequests(ctx context.Context, q querier, where string, args ...any) ([]domain.Request, error) {
	rows, err := q.Query(ctx, `SELECT `+requestColumns+` FROM dsr_requests `+where, args...)
	if err != nil {
		return nil, err
	}
	var out []domain.Request
	for rows.Next() {
		r, err := scanRequest(rows)
		if err != nil {
			rows.Close()
			return nil, err
		}
		out = append(out, *r)
	}
	rows.Close()
	if err := rows.Err(); err != nil {
		return nil, err
	}
	for i := range out {
		if err := loadTasks(ctx, q, &out[i]); err != nil {
			return nil, err
		}
	}
	return out, nil
}

func scanRequest(row pgx.Row) (*domain.Request, error) {
	var r domain.Request
	var typ, status string
	if err := row.Scan(&r.ID, &r.SubjectID, &typ, &status, &r.EmailSHA256, &r.DueAt, &r.Extended, &r.ExtensionReason,
		&r.CreatedAt, &r.CompletedAt, &r.Version); err != nil {
		return nil, err
	}
	r.Type, r.Status = domain.RequestType(typ), domain.RequestStatus(status)
	return &r, nil
}

func loadTasks(ctx context.Context, q querier, r *domain.Request) error {
	rows, err := q.Query(ctx, `SELECT processor, status, retention_reason, object_key, completed_at FROM dsr_tasks
		WHERE request_id = $1 ORDER BY processor`, r.ID)
	if err != nil {
		return err
	}
	defer rows.Close()
	r.Tasks = nil
	for rows.Next() {
		var t domain.Task
		var status string
		if err := rows.Scan(&t.Processor, &status, &t.RetentionReason, &t.ObjectKey, &t.CompletedAt); err != nil {
			return err
		}
		t.Status = domain.TaskStatus(status)
		r.Tasks = append(r.Tasks, t)
	}
	return rows.Err()
}

// UUIDv7 sinh id theo thời gian.
type UUIDv7 struct{}

func (UUIDv7) New() string { return uuid.Must(uuid.NewV7()).String() }
