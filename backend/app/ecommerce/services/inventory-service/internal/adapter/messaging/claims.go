package messaging

import (
	"context"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	invv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
)

// ClaimPublisher hiện thực app.ClaimPublisher bằng Kafka producer.
type ClaimPublisher struct{ P *kafka.Producer }

func (c ClaimPublisher) PublishClaim(ctx context.Context, claimID, saleID, productID, customerID string, qty int32) error {
	return c.P.Publish(ctx, TopicEvents, outbox.Message{
		AggregateType: AggregateType, AggregateID: saleID + ":" + productID,
		Payload: &invv1.FlashSaleClaimed{ClaimId: claimID, SaleId: saleID, ProductId: productID, CustomerId: customerID, Quantity: qty},
	})
}
