// Package cache: cache giá đã tính trên Redis. Đổi giá → INCR generation, khoá cũ tự hết hạn.
package cache

import (
	"context"
	"encoding/json"
	"errors"
	"time"

	goredis "github.com/redis/go-redis/v9"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/pricing-service/internal/domain"
)

const genKey = "pricing:gen"

type Redis struct{ rdb goredis.UniversalClient }

func New(rdb goredis.UniversalClient) *Redis { return &Redis{rdb: rdb} }

func (v *Redis) Generation(ctx context.Context) (int64, error) {
	n, err := v.rdb.Get(ctx, genKey).Int64()
	if errors.Is(err, goredis.Nil) {
		return 0, nil
	}
	return n, err
}

func (v *Redis) Bump(ctx context.Context) error { return v.rdb.Incr(ctx, genKey).Err() }

func (v *Redis) GetMany(ctx context.Context, keys []string) (map[string]domain.Resolved, error) {
	vals, err := v.rdb.MGet(ctx, keys...).Result()
	if err != nil {
		return nil, err
	}
	out := make(map[string]domain.Resolved, len(keys))
	for i, raw := range vals {
		s, ok := raw.(string)
		if !ok {
			continue
		}
		var r domain.Resolved
		if json.Unmarshal([]byte(s), &r) == nil {
			out[keys[i]] = r
		}
	}
	return out, nil
}

func (v *Redis) SetMany(ctx context.Context, items map[string]domain.Resolved, ttl time.Duration) error {
	_, err := v.rdb.Pipelined(ctx, func(p goredis.Pipeliner) error {
		for k, r := range items {
			b, err := json.Marshal(r)
			if err != nil {
				return err
			}
			p.Set(ctx, k, b, ttl)
		}
		return nil
	})
	return err
}
