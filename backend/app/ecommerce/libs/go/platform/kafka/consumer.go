// Package kafka bọc franz-go: consumer theo group, giải mã EventEnvelope, chống xử lý trùng
// (idempotent consumer) và nối trace từ producer.
package kafka

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"strconv"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/twmb/franz-go/pkg/kgo"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	"google.golang.org/protobuf/proto"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
)

// Handler xử lý một event trong transaction tx. Trả lỗi thì event được thử lại, quá số lần thì sang <topic>.dlq.
type Handler func(ctx context.Context, tx pgx.Tx, env *commonv1.EventEnvelope) error

type ConsumerConfig struct {
	Brokers []string
	Group   string
	Topics  []string
	// MaxAttempts: số lần chạy handler cho một record trước khi đẩy sang DLQ (mặc định 5).
	MaxAttempts int
}

const (
	defaultMaxAttempts = 5
	retryBaseDelay     = 200 * time.Millisecond
	retryMaxDelay      = 5 * time.Second
	// dlqBackoff: chờ trước khi đọc lại partition khi chính DLQ cũng không ghi được.
	dlqBackoff = 2 * time.Second
)

type Consumer struct {
	client      *kgo.Client
	pool        *pgxpool.Pool
	group       string
	handler     Handler
	maxAttempts int
	// produce đẩy record sang DLQ; tách ra để test không cần broker.
	produce func(ctx context.Context, r *kgo.Record) error
	sleep   func(ctx context.Context, d time.Duration) bool
}

func NewConsumer(cfg ConsumerConfig, pool *pgxpool.Pool, h Handler) (*Consumer, error) {
	c, err := kgo.NewClient(
		kgo.SeedBrokers(cfg.Brokers...),
		kgo.ConsumerGroup(cfg.Group),
		kgo.ConsumeTopics(cfg.Topics...),
		kgo.DisableAutoCommit(),
		// Không rebalance giữa lúc đang xử lý một lượt poll: tránh commit offset của partition đã bị thu hồi.
		kgo.BlockRebalanceOnPoll(),
		kgo.ConsumeResetOffset(kgo.NewOffset().AtStart()),
		kgo.RequiredAcks(kgo.AllISRAcks()),
	)
	if err != nil {
		return nil, err
	}
	cons := newConsumer(cfg, pool, h)
	cons.client = c
	cons.produce = func(ctx context.Context, r *kgo.Record) error { return c.ProduceSync(ctx, r).FirstErr() }
	return cons, nil
}

func newConsumer(cfg ConsumerConfig, pool *pgxpool.Pool, h Handler) *Consumer {
	attempts := cfg.MaxAttempts
	if attempts <= 0 {
		attempts = defaultMaxAttempts
	}
	return &Consumer{pool: pool, group: cfg.Group, handler: h, maxAttempts: attempts, sleep: sleepCtx}
}

// Run vòng lặp poll → xử lý → commit offset, tới khi ctx bị huỷ.
//
// Mỗi partition xử lý tuần tự. Record lỗi được thử lại tại chỗ có backoff; hết số lần thì đẩy sang
// <topic>.dlq kèm header lý do rồi đi tiếp. Chỉ khi không ghi được DLQ mới dừng partition đó và tua offset
// về đúng record lỗi để lượt sau đọc lại; các partition khác vẫn chạy. Chỉ commit record đã xong.
func (c *Consumer) Run(ctx context.Context) error {
	defer c.client.Close()
	for {
		fetches := c.client.PollFetches(ctx)
		if ctx.Err() != nil {
			c.client.AllowRebalance()
			return nil
		}
		fetches.EachError(func(t string, p int32, err error) {
			slog.ErrorContext(ctx, "kafka fetch", "topic", t, "partition", p, "err", err)
		})
		var done []*kgo.Record
		rewind := map[string]map[int32]kgo.EpochOffset{}
		fetches.EachPartition(func(p kgo.FetchTopicPartition) {
			ok, stuck := c.processPartition(ctx, p.Records)
			done = append(done, ok...)
			if stuck != nil {
				if rewind[stuck.Topic] == nil {
					rewind[stuck.Topic] = map[int32]kgo.EpochOffset{}
				}
				rewind[stuck.Topic][stuck.Partition] = kgo.EpochOffset{Epoch: stuck.LeaderEpoch, Offset: stuck.Offset}
			}
		})
		if len(done) > 0 {
			if err := c.client.CommitRecords(ctx, done...); err != nil && !errors.Is(err, context.Canceled) {
				slog.ErrorContext(ctx, "kafka commit", "err", err)
			}
		}
		if len(rewind) > 0 {
			c.client.SetOffsets(rewind)
		}
		c.client.AllowRebalance()
		if len(rewind) > 0 && !c.sleep(ctx, dlqBackoff) {
			return nil
		}
	}
}

// processPartition trả các record đã xong (xử lý được hoặc đã vào DLQ) và record đầu tiên bị kẹt (nếu có).
func (c *Consumer) processPartition(ctx context.Context, recs []*kgo.Record) (done []*kgo.Record, stuck *kgo.Record) {
	for _, r := range recs {
		if ctx.Err() != nil {
			return done, r
		}
		if err := c.deliver(ctx, r); err != nil {
			slog.ErrorContext(ctx, "kafka partition stuck", "topic", r.Topic, "partition", r.Partition, "offset", r.Offset, "err", err)
			return done, r
		}
		done = append(done, r)
	}
	return done, nil
}

// deliver chạy handler có retry; hết lượt thì đẩy sang DLQ. Chỉ trả lỗi khi record chưa được xử lý lẫn chưa vào DLQ.
func (c *Consumer) deliver(ctx context.Context, r *kgo.Record) error {
	var err error
	for attempt := 1; attempt <= c.maxAttempts; attempt++ {
		if err = c.handle(ctx, r); err == nil {
			return nil
		}
		var perm permanentError
		if errors.As(err, &perm) || attempt == c.maxAttempts {
			break
		}
		slog.WarnContext(ctx, "kafka handle failed, retrying", "topic", r.Topic, "offset", r.Offset,
			"attempt", attempt, "max", c.maxAttempts, "err", err)
		if !c.sleep(ctx, backoff(attempt)) {
			return ctx.Err()
		}
	}
	slog.ErrorContext(ctx, "kafka handle gave up, sending to DLQ", "topic", r.Topic, "offset", r.Offset, "err", err)
	if perr := c.produce(ctx, c.dlqRecord(r, err)); perr != nil {
		return fmt.Errorf("handle: %w; dlq: %v", err, perr)
	}
	return nil
}

func (c *Consumer) dlqRecord(r *kgo.Record, cause error) *kgo.Record {
	msg := cause.Error()
	if len(msg) > 1000 {
		msg = msg[:1000]
	}
	headers := append(append([]kgo.RecordHeader(nil), r.Headers...),
		kgo.RecordHeader{Key: "x-dlq-error", Value: []byte(msg)},
		kgo.RecordHeader{Key: "x-dlq-consumer", Value: []byte(c.group)},
		kgo.RecordHeader{Key: "x-dlq-topic", Value: []byte(r.Topic)},
		kgo.RecordHeader{Key: "x-dlq-partition", Value: []byte(strconv.Itoa(int(r.Partition)))},
		kgo.RecordHeader{Key: "x-dlq-offset", Value: []byte(strconv.FormatInt(r.Offset, 10))},
	)
	return &kgo.Record{Topic: r.Topic + ".dlq", Key: r.Key, Value: r.Value, Headers: headers}
}

// permanentError: thử lại cũng vô ích (ví dụ payload không giải mã được), đi thẳng sang DLQ.
type permanentError struct{ err error }

func (e permanentError) Error() string { return e.err.Error() }
func (e permanentError) Unwrap() error { return e.err }

func backoff(attempt int) time.Duration {
	d := retryBaseDelay << (attempt - 1)
	if d <= 0 || d > retryMaxDelay {
		return retryMaxDelay
	}
	return d
}

func sleepCtx(ctx context.Context, d time.Duration) bool {
	t := time.NewTimer(d)
	defer t.Stop()
	select {
	case <-ctx.Done():
		return false
	case <-t.C:
		return true
	}
}

func (c *Consumer) handle(ctx context.Context, r *kgo.Record) error {
	env := &commonv1.EventEnvelope{}
	if err := proto.Unmarshal(r.Value, env); err != nil {
		return permanentError{fmt.Errorf("decode envelope: %w", err)}
	}
	ctx = otel.GetTextMapPropagator().Extract(ctx, propagation.MapCarrier{"traceparent": env.GetTraceparent()})
	ctx, span := otel.Tracer("kafka").Start(ctx, "consume "+env.GetType())
	defer span.End()

	// Consumer không có DB (pool nil): handler tự idempotent (ví dụ search-service ghi Elasticsearch theo version).
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
