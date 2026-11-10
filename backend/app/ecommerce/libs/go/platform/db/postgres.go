// Package db mở pgx pool và chạy migration (goose) được nhúng trong binary.
package db

import (
	"context"
	"database/sql"
	"fmt"
	"io/fs"
	"time"

	"github.com/exaring/otelpgx"
	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/jackc/pgx/v5/stdlib"
	"github.com/pressly/goose/v3"
)

// Connect mở pool. schema khác rỗng thì mỗi service dùng schema Postgres riêng
// (tạo nếu chưa có) để không đụng bảng cũ do Liquibase của bản Java tạo ra.
func Connect(ctx context.Context, url, schema string) (*pgxpool.Pool, error) {
	cfg, err := pgxpool.ParseConfig(url)
	if err != nil {
		return nil, fmt.Errorf("parse DATABASE_URL: %w", err)
	}
	cfg.MaxConnLifetime = time.Hour
	cfg.ConnConfig.Tracer = otelpgx.NewTracer()
	if schema != "" {
		if err := ensureSchema(ctx, url, schema); err != nil {
			return nil, err
		}
		cfg.ConnConfig.RuntimeParams["search_path"] = schema
	}
	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, err
	}
	pctx, cancel := context.WithTimeout(ctx, 10*time.Second)
	defer cancel()
	if err := pool.Ping(pctx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping postgres: %w", err)
	}
	return pool, nil
}

// Migrate áp các file *.sql trong migrations (goose format). table là bảng version
// riêng cho từng service để nhiều service dùng chung một database không đè nhau.
func Migrate(ctx context.Context, pool *pgxpool.Pool, migrations fs.FS, table string) error {
	sqlDB := stdlib.OpenDBFromPool(pool)
	defer func(d *sql.DB) { _ = d.Close() }(sqlDB)
	p, err := goose.NewProvider(goose.DialectCustom, sqlDB, migrations,
		goose.WithStore(mustStore(table)))
	if err != nil {
		return err
	}
	_, err = p.Up(ctx)
	return err
}

func ensureSchema(ctx context.Context, url, schema string) error {
	conn, err := pgx.Connect(ctx, url)
	if err != nil {
		return fmt.Errorf("connect postgres: %w", err)
	}
	defer func() { _ = conn.Close(ctx) }()
	_, err = conn.Exec(ctx, "CREATE SCHEMA IF NOT EXISTS "+pgx.Identifier{schema}.Sanitize())
	return err
}
