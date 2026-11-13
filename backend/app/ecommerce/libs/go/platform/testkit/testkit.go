// Package testkit cấp Postgres/Redis cho test tích hợp.
//
// Có TEST_DATABASE_URL / TEST_REDIS_URL thì dùng luôn (CI với service container, hoặc `make up` ở local).
// Không có thì tự bật container bằng Testcontainers, một container cho mỗi tiến trình test (các test tự tách
// bằng schema/DB riêng); Ryuk dọn container khi tiến trình kết thúc. Cứ thế chạy `go test ./...` là đủ.
//
// Không có cả biến môi trường lẫn Docker: chạy local thì skip, còn trên CI (biến CI được đặt) thì fail,
// để test tích hợp không bị bỏ qua trong im lặng.
package testkit

import (
	"context"
	"os"
	"sync"
	"testing"
	"time"

	tcpostgres "github.com/testcontainers/testcontainers-go/modules/postgres"
	tcredis "github.com/testcontainers/testcontainers-go/modules/redis"
)

// Cùng image với CI (.github/workflows/ci-go.yml) và docker-compose local.
const (
	PostgresImage = "pgvector/pgvector:0.8.1-pg18-trixie"
	RedisImage    = "redis:8.10-alpine"
)

type lazy struct {
	once sync.Once
	url  string
	err  error
}

var pg, rd lazy

// PostgresURL trả URL tới một Postgres dùng được cho test.
func PostgresURL(t testing.TB) string {
	t.Helper()
	if url := os.Getenv("TEST_DATABASE_URL"); url != "" {
		return url
	}
	pg.once.Do(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
		defer cancel()
		c, err := tcpostgres.Run(ctx, PostgresImage,
			tcpostgres.WithDatabase("test"), tcpostgres.WithUsername("postgres"), tcpostgres.WithPassword("postgres"),
			tcpostgres.BasicWaitStrategies())
		if err != nil {
			pg.err = err
			return
		}
		pg.url, pg.err = c.ConnectionString(ctx, "sslmode=disable")
	})
	return need(t, "TEST_DATABASE_URL", &pg)
}

// RedisURL trả URL tới một Redis dùng được cho test (DB 15, giống CI).
func RedisURL(t testing.TB) string {
	t.Helper()
	if url := os.Getenv("TEST_REDIS_URL"); url != "" {
		return url
	}
	rd.once.Do(func() {
		ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
		defer cancel()
		c, err := tcredis.Run(ctx, RedisImage)
		if err != nil {
			rd.err = err
			return
		}
		url, err := c.ConnectionString(ctx)
		rd.url, rd.err = url+"/15", err
	})
	return need(t, "TEST_REDIS_URL", &rd)
}

func need(t testing.TB, env string, l *lazy) string {
	t.Helper()
	if l.err == nil {
		return l.url
	}
	if os.Getenv("CI") != "" {
		t.Fatalf("%s not set and Testcontainers failed: %v", env, l.err)
	}
	t.Skipf("%s not set and no test container could be started (is Docker running?): %v", env, l.err)
	return ""
}
