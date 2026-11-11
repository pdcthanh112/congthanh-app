import { timestampDate } from '@bufbuild/protobuf/wkt';
import { KafkaEventConsumer } from '@ecommerce/platform';
import {
  WishlistItemAddedSchema,
  WishlistItemRemovedSchema,
} from '@ecommerce/proto/ecommerce/customer/v1/customer_events_pb';
import {
  LowStockDetectedSchema,
  StockLevelChangedSchema,
} from '@ecommerce/proto/ecommerce/inventory/v1/inventory_events_pb';
import { MessageSentSchema } from '@ecommerce/proto/ecommerce/message/v1/message_events_pb';
import {
  OrderCancelledSchema,
  OrderConfirmedSchema,
  OrderPlacedSchema,
} from '@ecommerce/proto/ecommerce/order/v1/order_events_pb';
import { PaymentFailedSchema, PaymentRefundedSchema } from '@ecommerce/proto/ecommerce/payment/v1/payment_events_pb';
import { ProductUpsertedSchema } from '@ecommerce/proto/ecommerce/product/v1/product_events_pb';
import { ReviewRepliedSchema } from '@ecommerce/proto/ecommerce/review/v1/review_events_pb';
import type { EventHandlers } from '../../../application/event-handlers.js';

export const TOPICS = [
  'ecommerce.order.events',
  'ecommerce.payment.events',
  'ecommerce.review.events',
  'ecommerce.customer.events',
  'ecommerce.product.events',
  'ecommerce.inventory.events',
  'ecommerce.message.events',
];

/** Một consumer group cho mọi topic nguồn; kiểu event không đăng ký thì bị bỏ qua. */
export function domainEventsConsumer(h: EventHandlers, brokers: string[], clientId: string) {
  return new KafkaEventConsumer({ brokers, clientId, groupId: 'notification-service', topics: TOPICS })
    .on(OrderPlacedSchema, (e, env) => h.orderPlaced(env.id, e.orderId, e.customerId, e.total))
    .on(OrderConfirmedSchema, (e, env) => h.orderConfirmed(env.id, e.orderId))
    .on(OrderCancelledSchema, (e, env) => h.orderCancelled(env.id, e.orderId, e.reason))
    .on(PaymentFailedSchema, (e, env) => h.paymentFailed(env.id, e.orderId, e.reason))
    .on(PaymentRefundedSchema, (e, env) => h.paymentRefunded(env.id, e.orderId, e.amount))
    .on(ReviewRepliedSchema, (e, env) => h.reviewReplied(env.id, e.productId, e.customerId))
    .on(WishlistItemAddedSchema, (e, env) => h.wishlistAdded(env.id, e.customerId, e.productId))
    .on(WishlistItemRemovedSchema, (e, env) => h.wishlistRemoved(env.id, e.customerId, e.productId))
    .on(ProductUpsertedSchema, (e, env) => h.productUpserted(env.id, e.productId, e.name, e.price))
    .on(StockLevelChangedSchema, (e, env) => h.stockChanged(env.id, e.productId, e.available))
    .on(MessageSentSchema, (e, env) =>
      h.messageSent(
        env.id,
        e.conversationId,
        e.recipientIds,
        e.preview,
        e.sentAt ? timestampDate(e.sentAt) : new Date(),
      ),
    )
    .on(LowStockDetectedSchema, (e, env) => h.lowStock(env.id, e.productId, e.available));
}
