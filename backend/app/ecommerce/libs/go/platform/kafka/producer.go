package kafka

import (
	"context"

	"github.com/twmb/franz-go/pkg/kgo"
	"google.golang.org/protobuf/proto"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
)

// Producer gửi thẳng lên Kafka, CHỈ dùng cho luồng nóng không có transaction DB
// (vd. flash sale ghi nhận lượt giành hàng từ Redis). Mọi luồng khác dùng outbox.
type Producer struct {
	client *kgo.Client
	source string
}

func NewProducer(brokers []string, source string) (*Producer, error) {
	c, err := kgo.NewClient(kgo.SeedBrokers(brokers...), kgo.RequiredAcks(kgo.AllISRAcks()))
	if err != nil {
		return nil, err
	}
	return &Producer{client: c, source: source}, nil
}

func (p *Producer) Publish(ctx context.Context, topic string, m outbox.Message) error {
	env, _, err := outbox.Envelope(p.source, m, "")
	if err != nil {
		return err
	}
	b, err := proto.Marshal(env)
	if err != nil {
		return err
	}
	return p.client.ProduceSync(ctx, &kgo.Record{Topic: topic, Key: []byte(m.AggregateID), Value: b}).FirstErr()
}

func (p *Producer) Close() { p.client.Close() }
