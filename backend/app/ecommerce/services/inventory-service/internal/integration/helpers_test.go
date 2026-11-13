// Package integration_test chạy use case thật trên Postgres (+ Redis cho flash sale).
//
//	go test ./internal/integration/...   (tự bật container; hoặc đặt TEST_DATABASE_URL, TEST_REDIS_URL)
package integration_test

import (
	"context"
	"sync"
	"testing"

	goredis "github.com/redis/go-redis/v9"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/testkit"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/adapter/repository/redis"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
)

// fakeClaims thay Kafka producer: ghi lại các claim đã publish.
type fakeClaims struct {
	mu   sync.Mutex
	sent map[string]int32 // customer → qty
	all  []app.PublishedClaim
	// failWith != nil: failTimes lần gửi tới trả lỗi này (failTimes < 0 = luôn lỗi).
	failWith  error
	failTimes int
}

func (f *fakeClaims) PublishClaim(_ context.Context, c app.PublishedClaim) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.failWith != nil && f.failTimes != 0 {
		f.failTimes--
		return f.failWith
	}
	if f.sent == nil {
		f.sent = map[string]int32{}
	}
	f.sent[c.CustomerID] += c.Quantity
	f.all = append(f.all, c)
	return nil
}

func (f *fakeClaims) failNext(err error, times int) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.failWith, f.failTimes = err, times
}

func (f *fakeClaims) published(claimID string) int {
	f.mu.Lock()
	defer f.mu.Unlock()
	n := 0
	for _, c := range f.all {
		if c.ClaimID == claimID {
			n++
		}
	}
	return n
}

// reset xoá claim của test trước (biến dùng chung cả package).
func (f *fakeClaims) reset() {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.sent, f.all, f.failWith, f.failTimes = nil, nil, nil, 0
}

var claims = &fakeClaims{}

// flashStore trả Redis store (TEST_REDIS_URL hoặc container của testkit).
func flashStore(t *testing.T) app.FlashSaleStore {
	url := testkit.RedisURL(t)
	opt, err := goredis.ParseURL(url)
	if err != nil {
		t.Fatal(err)
	}
	rdb := goredis.NewClient(opt)
	t.Cleanup(func() { rdb.FlushDB(context.Background()); rdb.Close() })
	return redis.NewFlashSaleStore(rdb)
}
