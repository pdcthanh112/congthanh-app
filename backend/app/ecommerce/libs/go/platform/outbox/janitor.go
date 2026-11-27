package outbox

import (
	"context"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgconn"
)

// Debezium đọc event từ WAL (replication slot giữ WAL tới khi connector xác nhận), không đọc lại hàng trong bảng.
// Vì vậy xoá hàng outbox đã ghi không làm mất event; chỉ giữ một lúc để tra cứu khi debug.
// processed_events phải giữ lâu hơn retention của topic (7 ngày), nếu không consumer đọc lại từ đầu sẽ xử lý trùng.
const (
	DefaultJanitorInterval    = 5 * time.Minute
	DefaultOutboxRetention    = time.Hour
	DefaultProcessedRetention = 14 * 24 * time.Hour
	defaultPurgeBatch         = 5000
)

type JanitorConfig struct {
	Interval           time.Duration
	OutboxRetention    time.Duration
	ProcessedRetention time.Duration
	Batch              int
}

// DB là phần pgxpool.Pool mà janitor cần.
type DB interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
	QueryRow(ctx context.Context, sql string, args ...any) pgx.Row
}

type Janitor struct {
	db  DB
	cfg JanitorConfig
	now func() time.Time
}

func NewJanitor(db DB, cfg JanitorConfig) *Janitor {
	if cfg.Interval <= 0 {
		cfg.Interval = DefaultJanitorInterval
	}
	if cfg.OutboxRetention <= 0 {
		cfg.OutboxRetention = DefaultOutboxRetention
	}
	if cfg.ProcessedRetention <= 0 {
		cfg.ProcessedRetention = DefaultProcessedRetention
	}
	if cfg.Batch <= 0 {
		cfg.Batch = defaultPurgeBatch
	}
	return &Janitor{db: db, cfg: cfg, now: time.Now}
}

// Run dọn định kỳ tới khi ctx bị huỷ (dùng làm Background của server.Run).
func (j *Janitor) Run(ctx context.Context) error {
	t := time.NewTicker(j.cfg.Interval)
	defer t.Stop()
	for {
		j.PurgeOnce(ctx)
		select {
		case <-ctx.Done():
			return nil
		case <-t.C:
		}
	}
}

// PurgeOnce xoá outbox và processed_events quá hạn (bảng không tồn tại thì bỏ qua), rồi bảo trì partition.
func (j *Janitor) PurgeOnce(ctx context.Context) {
	now := j.now()
	for _, p := range []struct {
		table, column string
		before        time.Time
	}{
		{"outbox", "created_at", now.Add(-j.cfg.OutboxRetention)},
		{"processed_events", "processed_at", now.Add(-j.cfg.ProcessedRetention)},
	} {
		n, err := j.purge(ctx, p.table, p.column, p.before)
		if err != nil {
			if ctx.Err() == nil {
				slog.WarnContext(ctx, "purge failed", "table", p.table, "err", err)
			}
			continue
		}
		if n > 0 {
			slog.InfoContext(ctx, "purged old rows", "table", p.table, "rows", n)
		}
	}
	j.maintainPartitions(ctx)
}

// maintainPartitions gọi maintain_partitions() mà migration của service tạo khi service có bảng partition theo tháng,
// để luôn có sẵn partition các tháng tới; schema không có hàm này thì bỏ qua.
func (j *Janitor) maintainPartitions(ctx context.Context) {
	var exists bool
	if err := j.db.QueryRow(ctx, `SELECT to_regprocedure('maintain_partitions()') IS NOT NULL`).Scan(&exists); err != nil || !exists {
		if err != nil && ctx.Err() == nil {
			slog.WarnContext(ctx, "partition maintenance check failed", "err", err)
		}
		return
	}
	var created int
	if err := j.db.QueryRow(ctx, `SELECT maintain_partitions()`).Scan(&created); err != nil {
		if ctx.Err() == nil {
			slog.WarnContext(ctx, "partition maintenance failed", "err", err)
		}
		return
	}
	if created > 0 {
		slog.InfoContext(ctx, "created partitions", "partitions", created)
	}
}

func (j *Janitor) purge(ctx context.Context, table, column string, before time.Time) (int64, error) {
	var exists bool
	if err := j.db.QueryRow(ctx, `SELECT to_regclass($1) IS NOT NULL`, table).Scan(&exists); err != nil || !exists {
		return 0, err
	}
	// Xoá theo lô để không giữ khoá lâu và không tạo một transaction khổng lồ.
	q := fmt.Sprintf(`DELETE FROM %[1]s WHERE ctid = ANY(ARRAY(SELECT ctid FROM %[1]s WHERE %[2]s < $1 LIMIT $2))`, table, column)
	var total int64
	for {
		tag, err := j.db.Exec(ctx, q, before, j.cfg.Batch)
		if err != nil {
			return total, err
		}
		total += tag.RowsAffected()
		if tag.RowsAffected() < int64(j.cfg.Batch) {
			return total, nil
		}
	}
}
