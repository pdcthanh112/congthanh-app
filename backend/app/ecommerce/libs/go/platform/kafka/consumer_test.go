package kafka

import (
	"context"
	"errors"
	"testing"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/twmb/franz-go/pkg/kerr"
	"github.com/twmb/franz-go/pkg/kgo"
	"google.golang.org/protobuf/proto"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
)

func record(t *testing.T, offset int64, id string) *kgo.Record {
	t.Helper()
	b, err := proto.Marshal(&commonv1.EventEnvelope{Id: id, Type: "test"})
	if err != nil {
		t.Fatal(err)
	}
	return &kgo.Record{Topic: "ecommerce.test.events", Partition: 1, Offset: offset, Value: b}
}

func testConsumer(h Handler, produce func(context.Context, *kgo.Record) error) *Consumer {
	c := newConsumer(ConsumerConfig{Group: "g", MaxAttempts: 3}, nil, h)
	c.produce = produce
	c.sleep = func(context.Context, time.Duration) bool { return true }
	return c
}

func TestRetryThenSucceed(t *testing.T) {
	calls := 0
	c := testConsumer(func(context.Context, pgx.Tx, *commonv1.EventEnvelope) error {
		calls++
		if calls < 3 {
			return errors.New("tạm thời")
		}
		return nil
	}, func(context.Context, *kgo.Record) error { t.Fatal("không được vào DLQ"); return nil })

	done, stuck := c.processPartition(context.Background(), []*kgo.Record{record(t, 7, "e1")})
	if stuck != nil || len(done) != 1 || calls != 3 {
		t.Fatalf("done=%d stuck=%v calls=%d", len(done), stuck, calls)
	}
}

func TestExhaustedGoesToDLQAndContinues(t *testing.T) {
	var dlq []*kgo.Record
	c := testConsumer(func(_ context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		if env.GetId() == "bad" {
			return errors.New("hỏng")
		}
		return nil
	}, func(_ context.Context, r *kgo.Record) error { dlq = append(dlq, r); return nil })

	done, stuck := c.processPartition(context.Background(), []*kgo.Record{record(t, 1, "bad"), record(t, 2, "ok")})
	if stuck != nil || len(done) != 2 {
		t.Fatalf("done=%d stuck=%v", len(done), stuck)
	}
	if len(dlq) != 1 || dlq[0].Topic != "ecommerce.test.events.dlq" {
		t.Fatalf("dlq=%v", dlq)
	}
	h := map[string]string{}
	for _, x := range dlq[0].Headers {
		h[x.Key] = string(x.Value)
	}
	if h["x-dlq-offset"] != "1" || h["x-dlq-consumer"] != "g" || h["x-dlq-error"] != "hỏng" {
		t.Fatalf("headers=%v", h)
	}
}

func TestDLQFailureStopsPartitionAtFailedRecord(t *testing.T) {
	c := testConsumer(func(_ context.Context, _ pgx.Tx, env *commonv1.EventEnvelope) error {
		if env.GetId() == "bad" {
			return errors.New("hỏng")
		}
		return nil
	}, func(context.Context, *kgo.Record) error { return errors.New("broker down") })

	done, stuck := c.processPartition(context.Background(),
		[]*kgo.Record{record(t, 10, "ok"), record(t, 11, "bad"), record(t, 12, "ok")})
	if len(done) != 1 || done[0].Offset != 10 || stuck == nil || stuck.Offset != 11 {
		t.Fatalf("done=%v stuck=%v", done, stuck)
	}
}

func TestUndecodableGoesStraightToDLQ(t *testing.T) {
	calls, sent := 0, 0
	c := testConsumer(func(context.Context, pgx.Tx, *commonv1.EventEnvelope) error { calls++; return nil },
		func(context.Context, *kgo.Record) error { sent++; return nil })
	r := &kgo.Record{Topic: "t", Value: []byte{0xff, 0xff, 0xff}}
	if err := c.deliver(context.Background(), r); err != nil || calls != 0 || sent != 1 {
		t.Fatalf("err=%v calls=%d sent=%d", err, calls, sent)
	}
}

func TestClassifyPublishErrors(t *testing.T) {
	if err := classify(kerr.TopicAuthorizationFailed); !errors.Is(err, ErrNotDelivered) {
		t.Fatalf("authorization failure must be definite, got %v", err)
	}
	if err := classify(kerr.NotLeaderForPartition); errors.Is(err, ErrNotDelivered) {
		t.Fatalf("retriable broker error must stay ambiguous, got %v", err)
	}
	if err := classify(context.DeadlineExceeded); errors.Is(err, ErrNotDelivered) {
		t.Fatalf("timeout must stay ambiguous, got %v", err)
	}
	if classify(nil) != nil {
		t.Fatal("nil must stay nil")
	}
}
