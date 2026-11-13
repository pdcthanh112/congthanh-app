package messaging

import (
	"context"
	"errors"
	"fmt"

	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/kafka"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/platform/outbox"
	invv1 "github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/libs/go/proto/ecommerce/inventory/v1"
	"github.com/pdcthanh112/CongThanhApp/backend/app/ecommerce/services/inventory-service/internal/app"
)

// ClaimPublisher hiện thực app.ClaimPublisher bằng Kafka producer.
type ClaimPublisher struct{ P *kafka.Producer }

func (c ClaimPublisher) PublishClaim(ctx context.Context, in app.PublishedClaim) error {
	err := c.P.Publish(ctx, TopicEvents, outbox.Message{
		AggregateType: AggregateType, AggregateID: in.SaleID + ":" + in.ProductID,
		Payload: &invv1.FlashSaleClaimed{
			ClaimId: in.ClaimID, SaleId: in.SaleID, ProductId: in.ProductID, CustomerId: in.CustomerID,
			Quantity: in.Quantity, ExpiresAt: in.ExpiresAt.Unix(),
		},
	})
	if errors.Is(err, kafka.ErrNotDelivered) {
		return fmt.Errorf("%w: %w", app.ErrClaimNotPublished, err)
	}
	return err
}
