// Package outbox hiện thực Transactional Outbox: service ghi event vào bảng outbox
// trong CÙNG transaction với dữ liệu nghiệp vụ; Debezium (Outbox Event Router) đọc WAL
// của bảng này và đẩy lên Kafka. Không còn dual-write giữa DB và Kafka.
//
// Bảng (mỗi service tạo trong migration của mình):
//
//	CREATE TABLE outbox (
//	    id            UUID PRIMARY KEY,
//	    aggregatetype TEXT NOT NULL,   -- Debezium route → topic ecommerce.<aggregatetype>.events
//	    aggregateid   TEXT NOT NULL,   -- Kafka key
//	    type          TEXT NOT NULL,   -- vd. ecommerce.inventory.v1.InventoryReserved
//	    payload       BYTEA NOT NULL,  -- ecommerce.common.v1.EventEnvelope (protobuf)
//	    traceparent   TEXT,
//	    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
//	);
package outbox

import (
	"context"
	"fmt"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5/pgconn"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/propagation"
	"google.golang.org/protobuf/proto"
	"google.golang.org/protobuf/types/known/anypb"
	"google.golang.org/protobuf/types/known/timestamppb"

	commonv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/common/v1"
)

// Message là một event chuẩn bị ghi vào outbox.
type Message struct {
	AggregateType string // vd. "inventory"
	AggregateID   string
	Payload       proto.Message
}

// Execer là phần pgx.Tx mà outbox cần.
type Execer interface {
	Exec(ctx context.Context, sql string, args ...any) (pgconn.CommandTag, error)
}

// Write ghi các event vào bảng outbox bằng transaction tx của caller.
func Write(ctx context.Context, tx Execer, source string, msgs ...Message) error {
	carrier := propagation.MapCarrier{}
	otel.GetTextMapPropagator().Inject(ctx, carrier)
	tp := carrier["traceparent"]
	for _, m := range msgs {
		env, typ, err := Envelope(source, m, tp)
		if err != nil {
			return err
		}
		b, err := proto.Marshal(env)
		if err != nil {
			return err
		}
		if _, err := tx.Exec(ctx, `INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload, traceparent)
			VALUES ($1, $2, $3, $4, $5, $6)`, env.GetId(), m.AggregateType, m.AggregateID, typ, b, tp); err != nil {
			return fmt.Errorf("write outbox: %w", err)
		}
	}
	return nil
}

// Envelope bọc payload vào EventEnvelope.
func Envelope(source string, m Message, traceparent string) (*commonv1.EventEnvelope, string, error) {
	anyPayload, err := anypb.New(m.Payload)
	if err != nil {
		return nil, "", err
	}
	typ := string(m.Payload.ProtoReflect().Descriptor().FullName())
	return &commonv1.EventEnvelope{
		Id:            uuid.NewString(),
		Type:          typ,
		Source:        source,
		AggregateType: m.AggregateType,
		AggregateId:   m.AggregateID,
		OccurredAt:    timestamppb.New(time.Now()),
		Traceparent:   traceparent,
		Payload:       anyPayload,
	}, typ, nil
}

// MigrationSQL là DDL chuẩn của bảng outbox + bảng chống xử lý trùng cho consumer.
const MigrationSQL = `
CREATE TABLE IF NOT EXISTS outbox (
    id            UUID PRIMARY KEY,
    aggregatetype TEXT        NOT NULL,
    aggregateid   TEXT        NOT NULL,
    type          TEXT        NOT NULL,
    payload       BYTEA       NOT NULL,
    traceparent   TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     TEXT PRIMARY KEY,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);`
