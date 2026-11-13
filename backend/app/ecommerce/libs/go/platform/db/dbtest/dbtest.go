// Package dbtest tạo schema Postgres tạm cho test tích hợp: chạy migration goose, xoá schema khi test xong.
// Postgres lấy từ testkit: TEST_DATABASE_URL nếu có, không thì tự bật container (Testcontainers).
package dbtest

import (
	"context"
	"fmt"
	"io/fs"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/testkit"
)

func New(t *testing.T, prefix string, migrations fs.FS) *pgxpool.Pool {
	t.Helper()
	url := testkit.PostgresURL(t)
	ctx := context.Background()
	schema := fmt.Sprintf("%s_test_%d", strings.ReplaceAll(prefix, "-", "_"), time.Now().UnixNano())
	pool, err := db.Connect(ctx, url, schema)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_, _ = pool.Exec(context.Background(), "DROP SCHEMA "+schema+" CASCADE")
		pool.Close()
	})
	if err := db.Migrate(ctx, pool, migrations, "goose_db_version"); err != nil {
		t.Fatal(err)
	}
	return pool
}
