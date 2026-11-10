// Package redis hiện thực app.FlashSaleStore trên Redis/Valkey bằng Lua script.
package redis

import (
	"context"
	_ "embed"
	"fmt"
	"time"

	goredis "github.com/redis/go-redis/v9"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/domain"
)

var (
	//go:embed lua/claim.lua
	claimSrc string
	//go:embed lua/unclaim.lua
	unclaimSrc string
	//go:embed lua/drain.lua
	drainSrc string

	claimScript   = goredis.NewScript(claimSrc)
	unclaimScript = goredis.NewScript(unclaimSrc)
	drainScript   = goredis.NewScript(drainSrc)
)

type FlashSaleStore struct{ rdb goredis.UniversalClient }

func NewFlashSaleStore(rdb goredis.UniversalClient) *FlashSaleStore { return &FlashSaleStore{rdb: rdb} }

// Hash tag {sale:product} để hai key nằm cùng slot khi chạy Redis Cluster.
func keys(saleID, productID string) []string {
	tag := fmt.Sprintf("{flash:%s:%s}", saleID, productID)
	return []string{tag + ":state", tag + ":buyers"}
}

func (s *FlashSaleStore) Load(ctx context.Context, saleID, productID string, qty, limit int32, ttl time.Duration) error {
	k := keys(saleID, productID)
	ok, err := s.rdb.HSetNX(ctx, k[0], "initial", qty).Result()
	if err != nil {
		return err
	}
	if !ok {
		return domain.Conflict("flash sale %s/%s already started", saleID, productID)
	}
	pipe := s.rdb.TxPipeline()
	pipe.HSet(ctx, k[0], "remaining", qty, "limit", limit)
	pipe.Expire(ctx, k[0], ttl)
	pipe.Expire(ctx, k[1], ttl)
	_, err = pipe.Exec(ctx)
	return err
}

func (s *FlashSaleStore) Claim(ctx context.Context, saleID, productID, customerID string, qty int32) (int32, error) {
	n, err := claimScript.Run(ctx, s.rdb, keys(saleID, productID), customerID, qty).Int64()
	if err != nil {
		return 0, err
	}
	switch n {
	case -1:
		return 0, domain.Conflict("sold out")
	case -2:
		return 0, domain.Conflict("purchase limit per customer reached")
	case -3:
		return 0, domain.NotFound("flash sale is not running")
	}
	return int32(n), nil
}

func (s *FlashSaleStore) Unclaim(ctx context.Context, saleID, productID, customerID string, qty int32) error {
	return unclaimScript.Run(ctx, s.rdb, keys(saleID, productID), customerID, qty).Err()
}

func (s *FlashSaleStore) Drain(ctx context.Context, saleID, productID string) (int32, int32, error) {
	v, err := drainScript.Run(ctx, s.rdb, keys(saleID, productID)).Int64Slice()
	if err != nil {
		return 0, 0, err
	}
	if v[0] < 0 {
		return 0, 0, domain.NotFound("flash sale is not running")
	}
	return int32(v[0]), int32(v[1]), nil
}
