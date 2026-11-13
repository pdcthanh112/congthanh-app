package kafka

import (
	"context"
	"errors"
	"fmt"

	"github.com/twmb/franz-go/pkg/kerr"
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

// ErrNotDelivered: chắc chắn record KHÔNG được ghi (lỗi trước khi gửi, hoặc broker từ chối hẳn).
// Lỗi khác từ Publish (timeout, mất kết nối...) là mơ hồ: record có thể đã nằm trên broker.
var ErrNotDelivered = errors.New("kafka: record not delivered")

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
		return fmt.Errorf("%w: %v", ErrNotDelivered, err)
	}
	b, err := proto.Marshal(env)
	if err != nil {
		return fmt.Errorf("%w: %v", ErrNotDelivered, err)
	}
	return classify(p.client.ProduceSync(ctx, &kgo.Record{Topic: topic, Key: []byte(m.AggregateID), Value: b}).FirstErr())
}

// classify đánh dấu lỗi broker không retry được (quyền, record quá lớn, topic không tồn tại...) là ErrNotDelivered.
func classify(err error) error {
	var ke *kerr.Error
	if err != nil && errors.As(err, &ke) && !ke.Retriable {
		return fmt.Errorf("%w: %w", ErrNotDelivered, err)
	}
	return err
}

func (p *Producer) Close() { p.client.Close() }
