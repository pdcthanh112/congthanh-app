// Package redis hiện thực app.FlashSaleStore trên Redis bằng Lua script.
package redis

import (
	"context"
	_ "embed"
	"errors"
	"fmt"
	"strings"
	"time"

	goredis "github.com/redis/go-redis/v9"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

var (
	//go:embed lua/claim.lua
	claimSrc string
	//go:embed lua/release.lua
	releaseSrc string
	//go:embed lua/transition.lua
	transitionSrc string
	//go:embed lua/sweep.lua
	sweepSrc string
	//go:embed lua/queue.lua
	queueSrc string
	//go:embed lua/drain.lua
	drainSrc string

	claimScript      = goredis.NewScript(claimSrc)
	releaseScript    = goredis.NewScript(releaseSrc)
	transitionScript = goredis.NewScript(transitionSrc)
	sweepScript      = goredis.NewScript(sweepSrc)
	queueScript      = goredis.NewScript(queueSrc)
	drainScript      = goredis.NewScript(drainSrc)
)

const (
	activeKey = "flash:active" // set "sale\x1fproduct" của các sale đang chạy (cho sweeper)
	sep       = "\x1f"
)

type FlashSaleStore struct{ rdb goredis.UniversalClient }

func NewFlashSaleStore(rdb goredis.UniversalClient) *FlashSaleStore { return &FlashSaleStore{rdb: rdb} }

// Hash tag {sale:product} để mọi key của một sale nằm cùng slot khi chạy Redis Cluster.
// Thứ tự: state, buyers, claims, pending, queue (khớp KEYS[1..5] trong lua/).
func keys(saleID, productID string) []string {
	tag := fmt.Sprintf("{flash:%s:%s}", saleID, productID)
	return []string{tag + ":state", tag + ":buyers", tag + ":claims", tag + ":pending", tag + ":queue"}
}

// Chỉ mục claim_id → sale/product: saga chỉ biết id đơn (= claim id) khi giữ/chốt/trả hàng.
func claimIndexKey(claimID string) string { return "flash:claim:" + claimID }

func (s *FlashSaleStore) Load(ctx context.Context, saleID, productID string, cfg app.FlashSaleConfig) error {
	k := keys(saleID, productID)
	ok, err := s.rdb.HSetNX(ctx, k[0], "initial", cfg.Quantity).Result()
	if err != nil {
		return err
	}
	if !ok {
		return domain.Conflict("flash sale %s/%s already started", saleID, productID)
	}
	pipe := s.rdb.TxPipeline()
	pipe.HSet(ctx, k[0], "remaining", cfg.Quantity, "limit", cfg.PerCustomerLimit, "open", 0,
		"opens_at", cfg.OpensAt.UnixMilli(), "admit_rate", cfg.AdmitPerSecond, "admit_burst", cfg.AdmitBurst)
	pipe.Expire(ctx, k[0], cfg.TTL)
	pipe.SAdd(ctx, activeKey, saleID+sep+productID)
	_, err = pipe.Exec(ctx)
	return err
}

func (s *FlashSaleStore) Claim(ctx context.Context, c app.ClaimRequest) (int32, error) {
	k := keys(c.SaleID, c.ProductID)
	n, err := claimScript.Run(ctx, s.rdb, k, c.CustomerID, c.Quantity, c.ClaimID, c.Deadline.UnixMilli(), c.Now.UnixMilli()).Int64()
	if err != nil {
		return 0, err
	}
	if n >= 0 {
		// Chỉ ghi chỉ mục khi đã giành được (đa số lượt bấm là trượt, không tốn thêm lệnh nào). Ghi lỗi thì trả
		// suất; process chết ngay tại đây thì claim không có chỉ mục, không ai đặt đơn được và sweep sẽ thu hồi.
		ttl, err := s.rdb.PTTL(ctx, k[0]).Result()
		if err == nil {
			err = s.rdb.Set(ctx, claimIndexKey(c.ClaimID), c.SaleID+sep+c.ProductID, max(ttl, time.Minute)).Err()
		}
		if err != nil {
			releaseScript.Run(context.WithoutCancel(ctx), s.rdb, k[:4], c.ClaimID)
			return 0, err
		}
	}
	switch n {
	case -1:
		return 0, domain.Conflict("sold out")
	case -2:
		return 0, domain.Conflict("purchase limit per customer reached")
	case -3:
		return 0, domain.NotFound("flash sale is not running")
	case -4:
		return 0, domain.Conflict("join the waiting room first")
	case -5:
		return 0, domain.Conflict("it is not your turn yet")
	case -6:
		return 0, domain.Conflict("flash sale has not opened yet")
	}
	return int32(n), nil
}

func (s *FlashSaleStore) FindClaim(ctx context.Context, claimID string) (string, string, bool, error) {
	v, err := s.rdb.Get(ctx, claimIndexKey(claimID)).Result()
	if errors.Is(err, goredis.Nil) {
		return "", "", false, nil
	}
	if err != nil {
		return "", "", false, err
	}
	sale, product, ok := strings.Cut(v, sep)
	return sale, product, ok, nil
}

func (s *FlashSaleStore) ConfirmClaim(ctx context.Context, saleID, productID, claimID, customerID string, qty int32) error {
	n, err := transitionScript.Run(ctx, s.rdb, keys(saleID, productID)[:4], claimID, "CONFIRMED", customerID, qty).Int64()
	if err != nil {
		return err
	}
	switch n {
	case -1:
		return domain.Conflict("flash sale claim expired")
	case -2:
		return domain.Conflict("flash sale claim belongs to another customer")
	case -3:
		return domain.Conflict("order quantity differs from the flash sale claim")
	}
	return nil
}

func (s *FlashSaleStore) CommitClaim(ctx context.Context, saleID, productID, claimID string) (bool, error) {
	n, err := transitionScript.Run(ctx, s.rdb, keys(saleID, productID)[:4], claimID, "COMMITTED", "", 0).Int64()
	return n == 1, err
}

func (s *FlashSaleStore) ReleaseClaim(ctx context.Context, saleID, productID, claimID string) (app.ClaimRelease, error) {
	n, err := releaseScript.Run(ctx, s.rdb, keys(saleID, productID)[:4], claimID).Int64()
	if err != nil {
		return 0, err
	}
	switch n {
	case 1:
		return app.ClaimReleased, nil
	case -1:
		return app.ClaimCommitted, nil
	}
	return app.ClaimGone, nil
}

func (s *FlashSaleStore) SweepExpired(ctx context.Context, now time.Time, limit int) (int, int, error) {
	sales, err := s.rdb.SMembers(ctx, activeKey).Result()
	if err != nil {
		return 0, 0, err
	}
	released := 0
	for _, m := range sales {
		sale, product, _ := strings.Cut(m, sep)
		n, err := sweepScript.Run(ctx, s.rdb, keys(sale, product)[:4], now.UnixMilli(), limit).Int()
		if err != nil {
			return released, len(sales), err
		}
		released += n
	}
	return released, len(sales), nil
}

func (s *FlashSaleStore) Queue(ctx context.Context, saleID, productID, customerID string, join bool, now time.Time) (app.QueueState, error) {
	k := keys(saleID, productID)
	flag := "0"
	if join {
		flag = "1"
	}
	v, err := queueScript.Run(ctx, s.rdb, []string{k[0], k[4]}, customerID, flag, now.UnixMilli()).Int64Slice()
	if err != nil {
		return app.QueueState{}, err
	}
	if len(v) < 6 {
		return app.QueueState{}, domain.NotFound("flash sale is not running")
	}
	return app.QueueState{
		Rank: v[0], Admitted: v[1], Length: v[2], Remaining: int32(v[3]),
		OpensAt: time.UnixMilli(v[4]), AdmitPerSecond: int32(v[5]),
	}, nil
}

func (s *FlashSaleStore) Drain(ctx context.Context, saleID, productID string) (int32, int32, error) {
	v, err := drainScript.Run(ctx, s.rdb, keys(saleID, productID)).Int64Slice()
	if err != nil {
		return 0, 0, err
	}
	switch v[0] {
	case -1:
		return 0, 0, domain.NotFound("flash sale is not running")
	case -2:
		return 0, 0, domain.Conflict("%d flash sale claims are still in checkout; end the sale after they finish or expire", v[1])
	}
	s.rdb.SRem(ctx, activeKey, saleID+sep+productID)
	return int32(v[0]), int32(v[1]), nil
}
