// Package integration_test chạy use case thật trên Postgres (+ Redis cho flash sale).
//
//	TEST_DATABASE_URL=postgres://postgres@localhost:5432/test?sslmode=disable \
//	TEST_REDIS_URL=redis://localhost:6379/15 go test ./internal/integration/...
package integration_test

import (
	"context"
	"os"
	"sync"
	"testing"

	goredis "github.com/redis/go-redis/v9"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/repository/redis"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
)

// fakeClaims thay Kafka producer: ghi lại các claim đã publish.
type fakeClaims struct {
	mu   sync.Mutex
	sent map[string]int32 // customer → qty
	fail bool
}

func (f *fakeClaims) PublishClaim(_ context.Context, _, _, _, customerID string, qty int32) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.fail {
		return context.DeadlineExceeded
	}
	if f.sent == nil {
		f.sent = map[string]int32{}
	}
	f.sent[customerID] += qty
	return nil
}

var claims = &fakeClaims{}

// flashStore trả Redis store khi có TEST_REDIS_URL; nil thì flash sale tắt.
func flashStore(t *testing.T) app.FlashSaleStore {
	url := os.Getenv("TEST_REDIS_URL")
	if url == "" {
		return nil
	}
	opt, err := goredis.ParseURL(url)
	if err != nil {
		t.Fatal(err)
	}
	rdb := goredis.NewClient(opt)
	t.Cleanup(func() { rdb.FlushDB(context.Background()); rdb.Close() })
	return redis.NewFlashSaleStore(rdb)
}
