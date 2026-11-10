// Package kafka bọc franz-go: consumer theo group, giải mã EventEnvelope, chống xử lý trùng
// (idempotent consumer) và nối trace từ producer.
package kafka

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/twmb/franz-go/pkg/kgo"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	"google.golang.org/protobuf/proto"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
)

// Handler xử lý một event trong transaction tx. Trả lỗi thì event sẽ được thử lại.
type Handler func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error

type ConsumerConfig struct {
	Brokers []string
	Group   string
	Topics  []string
}

type Consumer struct {
	client  *kgo.Client
	pool    *pgxpool.Pool
	group   string
	handler Handler
}

func NewConsumer(cfg ConsumerConfig, pool *pgxpool.Pool, h Handler) (*Consumer, error) {
	c, err := kgo.NewClient(
		kgo.SeedBrokers(cfg.Brokers...),
		kgo.ConsumerGroup(cfg.Group),
		kgo.ConsumeTopics(cfg.Topics...),
		kgo.DisableAutoCommit(),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()),
	)
	if err != nil {
		return nil, err
	}
	return &Consumer{client: c, pool: pool, group: cfg.Group, handler: h}, nil
}

// Run vòng lặp poll → xử lý → commit offset, tới khi ctx bị huỷ.
func (c *Consumer) Run(ctx context.Context) error {
	defer c.client.Close()
	for {
		fetches := c.client.PollFetches(ctx)
		if ctx.Err() != nil {
			return nil
		}
		fetches.EachError(func(t string, p int32, err error) {
			slog.ErrorContext(ctx, "kafka fetch", "topic", t, "partition", p, "err", err)
		})
		var failed bool
		fetches.EachRecord(func(r *kgo.Record) {
			if failed {
				return
			}
			if err := c.handle(ctx, r); err != nil {
				slog.ErrorContext(ctx, "kafka handle", "topic", r.Topic, "offset", r.Offset, "err", err)
				failed = true
			}
		})
		if failed {
			// Không commit; lần poll sau đọc lại từ offset cũ. Chờ chút để không quay vòng nóng.
			select {
			case <-ctx.Done():
				return nil
			case <-time.After(2 * time.Second):
			}
			c.client.SetOffsets(nil)
			continue
		}
		if err := c.client.CommitUncommittedOffsets(ctx); err != nil && !errors.Is(err, context.Canceled) {
			slog.ErrorContext(ctx, "kafka commit", "err", err)
		}
	}
}

func (c *Consumer) handle(ctx context.Context, r *kgo.Record) error {
	env := &commonv1.EventEnvelope{}
	if err := proto.Unmarshal(r.Value, env); err != nil {
		slog.WarnContext(ctx, "skip undecodable event", "topic", r.Topic, "offset", r.Offset, "err", err)
		return nil
	}
	ctx = otel.GetTextMapPropagator().Extract(ctx, propagation.MapCarrier{"traceparent": env.GetTraceparent()})
	ctx, span := otel.Tracer("kafka").Start(ctx, "consume "+env.GetType())
	defer span.End()

	// Consumer không có DB (pool nil): handler tự idempotent (ví dụ search-service ghi OpenSearch theo version).
	if c.pool == nil {
		return c.handler(ctx, nil, env)
	}
	tx, err := c.pool.Begin(ctx)
	if err != nil {
		return err
	}
	defer func() { _ = tx.Rollback(ctx) }()
	tag, err := tx.Exec(ctx, `INSERT INTO processed_events (event_id, consumer) VALUES ($1, $2) ON CONFLICT DO NOTHING`, env.GetId(), c.group)
	if err != nil {
		return fmt.Errorf("mark processed: %w", err)
	}
	if tag.RowsAffected() == 0 {
		return nil // đã xử lý trước đó
	}
	if err := c.handler(ctx, tx, env); err != nil {
		return err
	}
	return tx.Commit(ctx)
}
