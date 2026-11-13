package outbox

import (
	"context"
	"fmt"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/testkit"
)

func TestJanitorPurgesOnlyExpiredRows(t *testing.T) {
	url := testkit.PostgresURL(t)
	ctx := context.Background()
	schema := fmt.Sprintf("janitor_test_%d", time.Now().UnixNano())
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		t.Fatal(err)
	}
	cfg.ConnConfig.RuntimeParams["search_path"] = schema
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DROP SCHEMA "+schema+" CASCADE")
		pool.Close()
	})
	if _, err := pool.Exec(ctx, "CREATE SCHEMA "+schema); err != nil {
		t.Fatal(err)
	}
	if _, err := pool.Exec(ctx, MigrationSQL); err != nil {
		t.Fatal(err)
	}
	for i := 0; i < 7; i++ {
		age := "3 hours"
		if i >= 5 {
			age = "1 minute"
		}
		if _, err := pool.Exec(ctx, `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload, created_at)
			VALUES (gen_random_uuid(), 'a', 'x', 't', '\x00', now() - $1::interval)`, age); err != nil {
			t.Fatal(err)
		}
	}
	if _, err := pool.Exec(ctx, `INSERT INTO processed_events (event_id, consumer, processed_at) VALUES
		('old', 'g', now() - interval '30 days'), ('new', 'g', now())`); err != nil {
		t.Fatal(err)
	}

	NewJanitor(pool, JanitorConfig{Batch: 2}).PurgeOnce(ctx)

	var outbox, processed int
	_ = pool.QueryRow(ctx, `SELECT count(*) FROM outbox`).Scan(&outbox)
	_ = pool.QueryRow(ctx, `SELECT count(*) FROM processed_events`).Scan(&processed)
	if outbox != 2 || processed != 1 {
		t.Fatalf("outbox=%d processed=%d, want 2 and 1", outbox, processed)
	}
}
