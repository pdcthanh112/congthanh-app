import { KafkaEventConsumer } from '@ecommerce/platform';
import {
  OrderCancelledSchema,
  OrderConfirmedSchema,
  OrderPlacedSchema,
} from '@ecommerce/proto/ecommerce/order/v1/order_events_pb';
import type { OrderEventsHandler } from '../../../application/order-events.handler.js';

/** Nghe ecommerce.order.events (Debezium từ outbox của order-service) để biết ai đã mua gì. */
export function orderEventsConsumer(handler: OrderEventsHandler, brokers: string[], clientId: string) {
  return new KafkaEventConsumer({ brokers, clientId, groupId: 'review-service', topics: ['ecommerce.order.events'] })
    .on(OrderPlacedSchema, (e, env) =>
      handler.orderPlaced(
        env.id,
        e.orderId,
        e.customerId,
        e.lines.map((l) => l.productId),
      ),
    )
    .on(OrderConfirmedSchema, (e, env) => handler.orderConfirmed(env.id, e.orderId))
    .on(OrderCancelledSchema, (e, env) => handler.orderCancelled(env.id, e.orderId));
}
