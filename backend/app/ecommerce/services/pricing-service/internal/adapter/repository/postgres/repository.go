// Package postgres là driven adapter Repository của pricing-service; PriceChanged ghi outbox cùng transaction.
package postgres

import (
	"context"
	"errors"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
	"github.com/jackc/pgx/v5/pgxpool"
	"google.golang.org/protobuf/types/known/timestamppb"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/apperr"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/money"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	pricingv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/pricing/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
)

const (
	Source        = "pricing-service"
	AggregateType = "pricing" // Debezium Outbox Router → topic ecommerce.pricing.events

	ReasonManual          = "MANUAL"
	ReasonScheduleStarted = "SCHEDULE_STARTED"
	ReasonScheduleEnded   = "SCHEDULE_ENDED"
	ReasonImported        = "IMPORTED"
	ReasonRemoved         = "REMOVED"
)

type Repository struct{ pool *pgxpool.Pool }

func New(pool *pgxpool.Pool) *Repository { return &Repository{pool: pool} }

func (r *Repository) Ping(ctx context.Context) error { return r.pool.Ping(ctx) }

type UUIDv7 struct{}

func (UUIDv7) NewID() string { return uuid.Must(uuid.NewV7()).String() }

var _ app.Repository = (*Repository)(nil)

// ---------- bảng giá ----------

const listCols = `id, code, name, currency, customer_group, priority, active, starts_at, ends_at`

func scanList(row pgx.CollectableRow) (domain.PriceList, error) {
	var l domain.PriceList
	err := row.Scan(&l.ID, &l.Code, &l.Name, &l.Currency, &l.CustomerGroup, &l.Priority, &l.Active, &l.StartsAt, &l.EndsAt)
	return l, err
}

func (r *Repository) ListsFor(ctx context.Context, currency string) ([]domain.PriceList, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+listCols+` FROM price_lists WHERE active AND currency = $1`, currency)
	return pgx.CollectRows(rows, scanList)
}

func (r *Repository) PriceLists(ctx context.Context) ([]domain.PriceList, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+listCols+` FROM price_lists ORDER BY priority DESC, code`)
	return pgx.CollectRows(rows, scanList)
}

func (r *Repository) PriceListByCode(ctx context.Context, code string) (domain.PriceList, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+listCols+` FROM price_lists WHERE code = $1`, code)
	l, err := pgx.CollectExactlyOneRow(rows, scanList)
	if errors.Is(err, pgx.ErrNoRows) {
		return l, apperr.NotFound("price list %q not found", code)
	}
	return l, err
}

func (r *Repository) CreatePriceList(ctx context.Context, l domain.PriceList) (domain.PriceList, error) {
	err := r.pool.QueryRow(ctx, `INSERT INTO price_lists (code, name, currency, customer_group, priority, active, starts_at, ends_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING id`,
		l.Code, l.Name, l.Currency, l.CustomerGroup, l.Priority, l.Active, l.StartsAt, l.EndsAt).Scan(&l.ID)
	var pg *pgconn.PgError
	if errors.As(err, &pg) && pg.Code == "23505" {
		return l, apperr.Conflict("price list %q already exists", l.Code)
	}
	return l, err
}

func (r *Repository) UpdatePriceList(ctx context.Context, l domain.PriceList) error {
	_, err := r.pool.Exec(ctx, `UPDATE price_lists SET name=$2, currency=$3, customer_group=$4, priority=$5, active=$6,
		starts_at=$7, ends_at=$8, updated_at=now() WHERE id=$1`,
		l.ID, l.Name, l.Currency, l.CustomerGroup, l.Priority, l.Active, l.StartsAt, l.EndsAt)
	return err
}

// ---------- tra giá ----------

func (r *Repository) Candidates(ctx context.Context, lists []domain.PriceList, productIDs []string, at time.Time) (map[string][]domain.Candidate, error) {
	ids := make([]int64, len(lists))
	byID := make(map[int64]domain.PriceList, len(lists))
	for i, l := range lists {
		ids[i] = l.ID
		byID[l.ID] = l
	}
	type key struct {
		list    int64
		product string
	}
	acc := map[key]*domain.Candidate{}
	get := func(list int64, product string) *domain.Candidate {
		k := key{list, product}
		if c, ok := acc[k]; ok {
			return c
		}
		c := &domain.Candidate{List: byID[list]}
		acc[k] = c
		return c
	}

	rows, err := r.pool.Query(ctx, `SELECT price_list_id, product_id, amount_minor, compare_at_minor FROM prices
		WHERE price_list_id = ANY($1) AND product_id = ANY($2)`, ids, productIDs)
	if err != nil {
		return nil, err
	}
	for rows.Next() {
		var p domain.Price
		if err := rows.Scan(&p.ListID, &p.ProductID, &p.Amount, &p.CompareAt); err != nil {
			rows.Close()
			return nil, err
		}
		get(p.ListID, p.ProductID).Base = &p
	}
	if err := rows.Err(); err != nil {
		return nil, err
	}

	rows, _ = r.pool.Query(ctx, `SELECT `+schedCols+` FROM price_schedules s JOIN price_lists l ON l.id = s.price_list_id
		WHERE s.price_list_id = ANY($1) AND s.product_id = ANY($2) AND s.status <> 'CANCELLED'
		  AND s.starts_at <= $3 AND (s.ends_at IS NULL OR s.ends_at > $3)`, ids, productIDs, at)
	scheds, err := pgx.CollectRows(rows, scanSchedule)
	if err != nil {
		return nil, err
	}
	for _, s := range scheds {
		c := get(s.ListID, s.ProductID)
		c.Schedules = append(c.Schedules, s)
	}

	out := make(map[string][]domain.Candidate, len(productIDs))
	for k, c := range acc {
		out[k.product] = append(out[k.product], *c)
	}
	return out, nil
}

// Lowest30d: giá thấp nhất của bảng giá công khai trong cửa sổ (giá cũ trước mỗi lần đổi cũng đã hiệu lực trong cửa sổ).
func (r *Repository) Lowest30d(ctx context.Context, productIDs []string, since time.Time) (map[string]int64, error) {
	rows, err := r.pool.Query(ctx, `SELECT h.product_id, min(LEAST(h.new_minor, COALESCE(h.old_minor, h.new_minor)))
		FROM price_history h JOIN price_lists l ON l.id = h.price_list_id
		WHERE l.code = 'default' AND h.product_id = ANY($1) AND h.changed_at >= $2 GROUP BY h.product_id`, productIDs, since)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]int64{}
	for rows.Next() {
		var id string
		var v int64
		if err := rows.Scan(&id, &v); err != nil {
			return nil, err
		}
		out[id] = v
	}
	return out, rows.Err()
}

// ---------- giá thường ----------

func priceChanged(l domain.PriceList, productID string, old *int64, now int64, reason string, at time.Time) outbox.Message {
	ev := &pricingv1.PriceChanged{ProductId: productID, PriceList: l.Code, NewPrice: money.FromMinor(now, l.Currency),
		Reason: reason, EffectiveAt: timestamppb.New(at)}
	if old != nil {
		ev.OldPrice = money.FromMinor(*old, l.Currency)
	}
	return outbox.Message{AggregateType: AggregateType, AggregateID: productID, Payload: ev}
}

func history(ctx context.Context, tx pgx.Tx, listID int64, productID string, old *int64, now int64, reason, by string, at time.Time) error {
	_, err := tx.Exec(ctx, `INSERT INTO price_history (price_list_id, product_id, old_minor, new_minor, reason, changed_by, changed_at)
		VALUES ($1,$2,$3,$4,$5,$6,$7)`, listID, productID, old, now, reason, by, at)
	return err
}

func (r *Repository) UpsertPrices(ctx context.Context, l domain.PriceList, ps []domain.Price, by string) (int, error) {
	return r.upsert(ctx, l, ps, by, ReasonManual, false)
}

// upsert: chỉ ghi lịch sử và event khi giá bán thực sự đổi. onlyNew = chỉ tạo giá chưa có (đồng bộ từ catalog).
func (r *Repository) upsert(ctx context.Context, l domain.PriceList, ps []domain.Price, by, reason string, onlyNew bool) (int, error) {
	changed := 0
	err := db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		changed = 0
		now := time.Now().UTC()
		var msgs []outbox.Message
		for _, p := range ps {
			var old *int64
			var oldCompare *int64
			err := tx.QueryRow(ctx, `SELECT amount_minor, compare_at_minor FROM prices WHERE price_list_id=$1 AND product_id=$2 FOR UPDATE`,
				l.ID, p.ProductID).Scan(&old, &oldCompare)
			if err != nil && !errors.Is(err, pgx.ErrNoRows) {
				return err
			}
			if old != nil && (onlyNew || (*old == p.Amount && eq(oldCompare, p.CompareAt))) {
				continue
			}
			if _, err := tx.Exec(ctx, `INSERT INTO prices (price_list_id, product_id, amount_minor, compare_at_minor, updated_at, updated_by)
				VALUES ($1,$2,$3,$4,$5,$6) ON CONFLICT (price_list_id, product_id) DO UPDATE
				SET amount_minor=EXCLUDED.amount_minor, compare_at_minor=EXCLUDED.compare_at_minor, updated_at=EXCLUDED.updated_at, updated_by=EXCLUDED.updated_by`,
				l.ID, p.ProductID, p.Amount, p.CompareAt, now, by); err != nil {
				return err
			}
			changed++
			if old != nil && *old == p.Amount {
				continue // chỉ đổi giá gạch ngang
			}
			if err := history(ctx, tx, l.ID, p.ProductID, old, p.Amount, reason, by, now); err != nil {
				return err
			}
			msgs = append(msgs, priceChanged(l, p.ProductID, old, p.Amount, reason, now))
		}
		if len(msgs) == 0 {
			return nil
		}
		return outbox.Write(ctx, tx, Source, msgs...)
	})
	return changed, err
}

func eq(a, b *int64) bool { return (a == nil && b == nil) || (a != nil && b != nil && *a == *b) }

func (r *Repository) DeletePrice(ctx context.Context, l domain.PriceList, productID, by string) error {
	if l.Code == domain.DefaultList {
		return apperr.Invalid("prices in the default list cannot be removed, only changed")
	}
	tag, err := r.pool.Exec(ctx, `DELETE FROM prices WHERE price_list_id=$1 AND product_id=$2`, l.ID, productID)
	if err == nil && tag.RowsAffected() == 0 {
		return apperr.NotFound("no price for %s in %s", productID, l.Code)
	}
	return err
}

func (r *Repository) PricesInList(ctx context.Context, listID int64, productID string, limit, offset int) ([]domain.Price, error) {
	rows, _ := r.pool.Query(ctx, `SELECT price_list_id, product_id, amount_minor, compare_at_minor FROM prices
		WHERE price_list_id=$1 AND ($2 = '' OR product_id = $2) ORDER BY product_id LIMIT $3 OFFSET $4`, listID, productID, limit, offset)
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (domain.Price, error) {
		var p domain.Price
		err := row.Scan(&p.ListID, &p.ProductID, &p.Amount, &p.CompareAt)
		return p, err
	})
}

func (r *Repository) BasePrices(ctx context.Context, listID int64, productIDs []string) (map[string]int64, error) {
	rows, err := r.pool.Query(ctx, `SELECT product_id, amount_minor FROM prices WHERE price_list_id=$1 AND product_id = ANY($2)`, listID, productIDs)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := map[string]int64{}
	for rows.Next() {
		var id string
		var v int64
		if err := rows.Scan(&id, &v); err != nil {
			return nil, err
		}
		out[id] = v
	}
	return out, rows.Err()
}

// ---------- đợt sale ----------

const schedCols = `s.id, s.price_list_id, l.code, s.product_id, s.kind, s.amount_minor, s.starts_at, s.ends_at, s.status, s.reason, s.created_by`

func scanSchedule(row pgx.CollectableRow) (domain.Schedule, error) {
	var s domain.Schedule
	err := row.Scan(&s.ID, &s.ListID, &s.ListCode, &s.ProductID, &s.Kind, &s.Amount, &s.StartsAt, &s.EndsAt, &s.Status, &s.Reason, &s.CreatedBy)
	return s, err
}

func (r *Repository) CreateSchedules(ctx context.Context, ss []domain.Schedule) error {
	return db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		for _, s := range ss {
			if _, err := tx.Exec(ctx, `INSERT INTO price_schedules (id, price_list_id, product_id, kind, amount_minor, starts_at, ends_at, status, reason, created_by)
				VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`, s.ID, s.ListID, s.ProductID, s.Kind, s.Amount, s.StartsAt, s.EndsAt, s.Status, s.Reason, s.CreatedBy); err != nil {
				return err
			}
		}
		return nil
	})
}

func (r *Repository) one(ctx context.Context, q pgx.Tx, id string, lock bool) (domain.Schedule, error) {
	sql := `SELECT ` + schedCols + ` FROM price_schedules s JOIN price_lists l ON l.id = s.price_list_id WHERE s.id = $1`
	if lock {
		sql += ` FOR UPDATE OF s`
	}
	rows, _ := q.Query(ctx, sql, id)
	s, err := pgx.CollectExactlyOneRow(rows, scanSchedule)
	if errors.Is(err, pgx.ErrNoRows) {
		return s, apperr.NotFound("schedule %s not found", id)
	}
	return s, err
}

// CancelSchedule: huỷ đợt sale; nếu đang chạy thì phát PriceChanged trả về giá thường.
func (r *Repository) CancelSchedule(ctx context.Context, id string) (domain.Schedule, error) {
	if _, err := uuid.Parse(id); err != nil {
		return domain.Schedule{}, apperr.NotFound("schedule %s not found", id)
	}
	var out domain.Schedule
	err := db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		s, err := r.one(ctx, tx, id, true)
		if err != nil {
			return err
		}
		if s.Status == domain.Ended || s.Status == domain.Cancelled {
			return apperr.Conflict("schedule is already %s", s.Status)
		}
		if _, err := tx.Exec(ctx, `UPDATE price_schedules SET status='CANCELLED' WHERE id=$1`, id); err != nil {
			return err
		}
		wasActive := s.Status == domain.Active
		s.Status = domain.Cancelled
		out = s
		if !wasActive {
			return nil
		}
		return r.endEffects(ctx, tx, s, ReasonScheduleEnded, "cancel", time.Now())
	})
	return out, err
}

func (r *Repository) Schedules(ctx context.Context, f app.ScheduleFilter) ([]domain.Schedule, error) {
	rows, _ := r.pool.Query(ctx, `SELECT `+schedCols+` FROM price_schedules s JOIN price_lists l ON l.id = s.price_list_id
		WHERE ($1 = '' OR s.product_id = $1) AND ($2 = '' OR s.status = $2) ORDER BY s.starts_at DESC LIMIT $3`,
		f.ProductID, string(f.Status), f.Limit)
	return pgx.CollectRows(rows, scanSchedule)
}

func (r *Repository) History(ctx context.Context, productID string, limit int) ([]app.HistoryEntry, error) {
	rows, _ := r.pool.Query(ctx, `SELECT l.code, h.product_id, h.old_minor, h.new_minor, h.reason, h.changed_by, h.changed_at
		FROM price_history h JOIN price_lists l ON l.id = h.price_list_id WHERE h.product_id = $1 ORDER BY h.changed_at DESC, h.id DESC LIMIT $2`,
		productID, limit)
	return pgx.CollectRows(rows, func(row pgx.CollectableRow) (app.HistoryEntry, error) {
		var e app.HistoryEntry
		err := row.Scan(&e.PriceList, &e.ProductID, &e.Old, &e.New, &e.Reason, &e.ChangedBy, &e.ChangedAt)
		return e, err
	})
}

func (r *Repository) listByID(ctx context.Context, tx pgx.Tx, id int64) (domain.PriceList, error) {
	rows, _ := tx.Query(ctx, `SELECT `+listCols+` FROM price_lists WHERE id = $1`, id)
	return pgx.CollectExactlyOneRow(rows, scanList)
}

func (r *Repository) baseOf(ctx context.Context, tx pgx.Tx, listID int64, productID string) (*int64, error) {
	var v int64
	err := tx.QueryRow(ctx, `SELECT amount_minor FROM prices WHERE price_list_id=$1 AND product_id=$2`, listID, productID).Scan(&v)
	if errors.Is(err, pgx.ErrNoRows) {
		return nil, nil
	}
	return &v, err
}

func (r *Repository) startEffects(ctx context.Context, tx pgx.Tx, s domain.Schedule) error {
	l, err := r.listByID(ctx, tx, s.ListID)
	if err != nil {
		return err
	}
	base, err := r.baseOf(ctx, tx, s.ListID, s.ProductID)
	if err != nil {
		return err
	}
	at := s.StartsAt
	if err := history(ctx, tx, s.ListID, s.ProductID, base, s.Amount, ReasonScheduleStarted, "scheduler", at); err != nil {
		return err
	}
	return outbox.Write(ctx, tx, Source, priceChanged(l, s.ProductID, base, s.Amount, ReasonScheduleStarted, at))
}

// endEffects: đợt sale hết hiệu lực → giá quay về giá thường (nếu có).
func (r *Repository) endEffects(ctx context.Context, tx pgx.Tx, s domain.Schedule, reason, by string, now time.Time) error {
	l, err := r.listByID(ctx, tx, s.ListID)
	if err != nil {
		return err
	}
	base, err := r.baseOf(ctx, tx, s.ListID, s.ProductID)
	if err != nil || base == nil {
		return err
	}
	at := now.UTC()
	if s.EndsAt != nil && s.EndsAt.Before(at) {
		at = *s.EndsAt
	}
	old := s.Amount
	if err := history(ctx, tx, s.ListID, s.ProductID, &old, *base, reason, by, at); err != nil {
		return err
	}
	return outbox.Write(ctx, tx, Source, priceChanged(l, s.ProductID, &old, *base, reason, at))
}

// AdvanceSchedules: SKIP LOCKED để nhiều pod chạy song song không phát trùng event.
// Đợt đã qua cả giờ kết thúc khi scheduler còn ở SCHEDULED được chuyển thẳng sang ENDED (không phát event).
func (r *Repository) AdvanceSchedules(ctx context.Context, now time.Time) (started, ended int, err error) {
	err = db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		started, ended = 0, 0
		if _, err := tx.Exec(ctx, `UPDATE price_schedules SET status='ENDED'
			WHERE status='SCHEDULED' AND ends_at IS NOT NULL AND ends_at <= $1`, now); err != nil {
			return err
		}
		rows, _ := tx.Query(ctx, `SELECT `+schedCols+` FROM price_schedules s JOIN price_lists l ON l.id = s.price_list_id
			WHERE s.status='SCHEDULED' AND s.starts_at <= $1 ORDER BY s.starts_at LIMIT 500 FOR UPDATE OF s SKIP LOCKED`, now)
		due, err := pgx.CollectRows(rows, scanSchedule)
		if err != nil {
			return err
		}
		for _, s := range due {
			if _, err := tx.Exec(ctx, `UPDATE price_schedules SET status='ACTIVE' WHERE id=$1`, s.ID); err != nil {
				return err
			}
			if err := r.startEffects(ctx, tx, s); err != nil {
				return err
			}
			started++
		}
		rows, _ = tx.Query(ctx, `SELECT `+schedCols+` FROM price_schedules s JOIN price_lists l ON l.id = s.price_list_id
			WHERE s.status='ACTIVE' AND s.ends_at IS NOT NULL AND s.ends_at <= $1 ORDER BY s.ends_at LIMIT 500 FOR UPDATE OF s SKIP LOCKED`, now)
		over, err := pgx.CollectRows(rows, scanSchedule)
		if err != nil {
			return err
		}
		for _, s := range over {
			if _, err := tx.Exec(ctx, `UPDATE price_schedules SET status='ENDED' WHERE id=$1`, s.ID); err != nil {
				return err
			}
			if err := r.endEffects(ctx, tx, s, ReasonScheduleEnded, "scheduler", now); err != nil {
				return err
			}
			ended++
		}
		return nil
	})
	return started, ended, err
}

// ---------- đồng bộ catalog ----------

// SeedFromCatalog: sản phẩm mới tạo bên catalog có giá → tạo giá thường trong bảng "default" nếu chưa có.
// Sau đó pricing-service là nguồn sự thật, giá trong catalog không ghi đè.
func (r *Repository) SeedFromCatalog(ctx context.Context, productID, currency string, amount int64) error {
	l, err := r.PriceListByCode(ctx, domain.DefaultList)
	if err != nil || l.Currency != currency {
		return err
	}
	_, err = r.upsert(ctx, l, []domain.Price{{ListID: l.ID, ProductID: productID, Amount: amount}}, "catalog", ReasonImported, true)
	return err
}

// RemoveProduct: sản phẩm bị xoá → bỏ giá và huỷ mọi đợt sale chưa kết thúc.
func (r *Repository) RemoveProduct(ctx context.Context, productID string) error {
	return db.InTx(ctx, r.pool, func(tx pgx.Tx) error {
		if _, err := tx.Exec(ctx, `UPDATE price_schedules SET status='CANCELLED' WHERE product_id=$1 AND status IN ('SCHEDULED','ACTIVE')`, productID); err != nil {
			return err
		}
		_, err := tx.Exec(ctx, `DELETE FROM prices WHERE product_id=$1`, productID)
		return err
	})
}
