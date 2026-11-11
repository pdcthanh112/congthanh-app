// Package kafka: adapter Publisher ghi event JSON lên Kafka bằng franz-go.
// Clickstream không đi qua outbox vì không có transaction DB; mất vài event khi broker sập là chấp nhận được.
package kafka

import (
	"context"
	"encoding/json"

	"github.com/twmb/franz-go/pkg/kgo"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/data/ingestion/clickstream-collector/internal/domain"
)

type Publisher struct {
	client *kgo.Client
	topic  string
}

func NewPublisher(brokers []string, topic string) (*Publisher, error) {
	c, err := kgo.NewClient(
		kgo.SeedBrokers(brokers...),
		kgo.RequiredAcks(kgo.LeaderAck()),
		kgo.DisableIdempotentWrite(), // LeaderAck không đi cùng idempotent producer
		kgo.ProducerBatchCompression(kgo.ZstdCompression()),
		kgo.AllowAutoTopicCreation(),
	)
	if err != nil {
		return nil, err
	}
	return &Publisher{client: c, topic: topic}, nil
}

// Publish gửi cả lô và chờ broker xác nhận; key = session_id để event của một phiên giữ thứ tự.
func (p *Publisher) Publish(ctx context.Context, events []domain.Event) error {
	recs := make([]*kgo.Record, 0, len(events))
	for i := range events {
		b, err := json.Marshal(&events[i])
		if err != nil {
			return err
		}
		recs = append(recs, &kgo.Record{
			Topic: p.topic,
			Key:   []byte(events[i].SessionID),
			Value: b,
			Headers: []kgo.RecordHeader{
				{Key: "ce_type", Value: []byte("clickstream." + string(events[i].EventType))},
				{Key: "ce_id", Value: []byte(events[i].EventID)},
			},
		})
	}
	return p.client.ProduceSync(ctx, recs...).FirstErr()
}

func (p *Publisher) Ping(ctx context.Context) error { return p.client.Ping(ctx) }

func (p *Publisher) Close() { p.client.Close() }
