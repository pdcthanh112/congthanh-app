// Package dbtest tạo schema Postgres tạm cho test tích hợp: chạy migration goose, xoá schema khi test xong.
// Bỏ qua test khi không có TEST_DATABASE_URL.
//
//	TEST_DATABASE_URL=postgres://postgres@localhost:5432/test?sslmode=disable go test ./...
package dbtest

import (
	"context"
	"fmt"
	"io/fs"
	"os"
	"strings"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/db"
)

func New(t *testing.T, prefix string, migrations fs.FS) *pgxpool.Pool {
	t.Helper()
	url := os.Getenv("TEST_DATABASE_URL")
	if url == "" {
		t.Skip("TEST_DATABASE_URL not set")
	}
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
