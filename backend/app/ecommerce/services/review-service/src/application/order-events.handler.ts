import { Inject, Injectable } from '@nestjs/common';
import { CLOCK, type Clock, UNIT_OF_WORK, type UnitOfWork } from './ports.js';

const CONSUMER = 'review-service.purchases';

/**
 * Dựng bảng "đã mua" từ event đơn hàng (Kafka giao ít nhất một lần → markProcessed chống xử lý lặp).
 * Thứ tự: các event cùng order_id cùng partition nên OrderPlaced luôn tới trước Confirmed/Cancelled.
 */
@Injectable()
export class OrderEventsHandler {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(CLOCK) private readonly clock: Clock,
  ) {}

  orderPlaced(eventId: string, orderId: string, customerId: string, productIds: readonly string[]) {
    return this.uow.run(async (tx) => {
      if (!(await tx.markProcessed(eventId, CONSUMER))) return;
      await tx.purchases.upsertPlaced(orderId, customerId, [...new Set(productIds)]);
    });
  }

  orderConfirmed(eventId: string, orderId: string) {
    return this.uow.run(async (tx) => {
      if (!(await tx.markProcessed(eventId, CONSUMER))) return;
      await tx.purchases.setStatus(orderId, 'CONFIRMED', this.clock.now());
    });
  }

  orderCancelled(eventId: string, orderId: string) {
    return this.uow.run(async (tx) => {
      if (!(await tx.markProcessed(eventId, CONSUMER))) return;
      await tx.purchases.setStatus(orderId, 'CANCELLED', this.clock.now());
    });
  }
}
