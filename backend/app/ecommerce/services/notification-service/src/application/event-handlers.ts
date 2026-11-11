import { Inject, Injectable, Logger } from '@nestjs/common';
import type { Channel, Notification } from '../domain/notification.js';
import { formatMoney, type MoneyLike, templates } from '../domain/templates.js';
import { NotificationService } from './notification.service.js';
import {
  RECIPIENT_DIRECTORY,
  type RecipientDirectory,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

const ALL: Channel[] = ['IN_APP', 'EMAIL', 'SMS', 'WEB_PUSH', 'FCM'];
const SOFT: Channel[] = ['IN_APP', 'WEB_PUSH', 'FCM', 'EMAIL'];
const CONSUMER = 'notification-service';

const toMinor = (m: MoneyLike | undefined) => (m ? Number(m.units) * 100 + Math.round(m.nanos / 1e7) : undefined);

/**
 * Biến event miền thành thông báo. Mỗi handler chạy trong một transaction: markProcessed + bản sao cục bộ +
 * thông báo + hàng đợi gửi cùng commit, nên Kafka giao lại cũng không gửi trùng.
 */
@Injectable()
export class EventHandlers {
  private readonly log = new Logger(EventHandlers.name);

  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(RECIPIENT_DIRECTORY) private readonly directory: RecipientDirectory,
    private readonly notifications: NotificationService,
  ) {}

  private async handle(eventId: string, work: (tx: TransactionScope) => Promise<(Notification | undefined)[]>) {
    const created = await this.uow.run(async (tx) => {
      if (!(await tx.markProcessed(eventId, CONSUMER))) return [];
      return work(tx);
    });
    await this.notifications.publish(created);
  }

  // ---------- đơn hàng / thanh toán ----------

  orderPlaced(eventId: string, orderId: string, customerId: string, total: MoneyLike | undefined) {
    return this.handle(eventId, async (tx) => {
      await tx.views.rememberOrder(orderId, customerId);
      return [
        await this.notifications.notifyIn(tx, {
          userId: customerId,
          category: 'ORDER',
          type: 'ORDER_PLACED',
          message: templates.orderPlaced(orderId, formatMoney(total)),
          dedupKey: `${eventId}:${customerId}`,
          channels: ['IN_APP', 'EMAIL', 'WEB_PUSH', 'FCM'],
        }),
      ];
    });
  }

  orderConfirmed(eventId: string, orderId: string) {
    return this.forOrder(eventId, orderId, 'ORDER', 'ORDER_CONFIRMED', templates.orderConfirmed(orderId), ALL);
  }

  orderCancelled(eventId: string, orderId: string, reason: string) {
    return this.forOrder(eventId, orderId, 'ORDER', 'ORDER_CANCELLED', templates.orderCancelled(orderId, reason), ALL);
  }

  paymentFailed(eventId: string, orderId: string, reason: string) {
    return this.forOrder(eventId, orderId, 'PAYMENT', 'PAYMENT_FAILED', templates.paymentFailed(orderId, reason), ALL);
  }

  paymentRefunded(eventId: string, orderId: string, amount: MoneyLike | undefined) {
    const m = templates.paymentRefunded(orderId, formatMoney(amount));
    return this.forOrder(eventId, orderId, 'PAYMENT', 'PAYMENT_REFUNDED', m, ['IN_APP', 'EMAIL', 'FCM']);
  }

  private forOrder(
    eventId: string,
    orderId: string,
    category: 'ORDER' | 'PAYMENT',
    type: string,
    message: ReturnType<typeof templates.orderConfirmed>,
    channels: Channel[],
  ) {
    return this.handle(eventId, async (tx) => {
      const customerId = await tx.views.customerOfOrder(orderId);
      if (!customerId) {
        // OrderPlaced luôn tới trước (cùng partition theo order_id); thiếu nghĩa là đơn tạo trước khi service này chạy.
        this.log.warn(`no customer known for order ${orderId}; ${type} skipped`);
        return [];
      }
      return [
        await this.notifications.notifyIn(tx, {
          userId: customerId,
          category,
          type,
          message,
          dedupKey: `${eventId}:${customerId}`,
          channels,
        }),
      ];
    });
  }

  // ---------- đánh giá ----------

  reviewReplied(eventId: string, productId: string, customerId: string) {
    return this.handle(eventId, async (tx) => [
      await this.notifications.notifyIn(tx, {
        userId: customerId,
        category: 'REVIEW',
        type: 'REVIEW_REPLIED',
        message: templates.reviewReplied(productId),
        dedupKey: `${eventId}:${customerId}`,
        channels: SOFT,
      }),
    ]);
  }

  // ---------- tin nhắn ----------

  /**
   * Báo tin nhắn mới qua push/in-app, gom theo khung 10 phút mỗi hội thoại (dedupKey theo khung):
   * đang chat liên tục thì chỉ nhận một thông báo.
   */
  messageSent(eventId: string, conversationId: string, recipientIds: readonly string[], preview: string, sentAt: Date) {
    const bucket = Math.floor(sentAt.getTime() / 600_000);
    return this.handle(eventId, async (tx) =>
      Promise.all(
        recipientIds.map((userId) =>
          this.notifications.notifyIn(tx, {
            userId,
            category: 'MESSAGE',
            type: 'NEW_MESSAGE',
            message: templates.newMessage(conversationId, preview),
            dedupKey: `msg:${conversationId}:${userId}:${bucket}`,
            channels: ['IN_APP', 'WEB_PUSH', 'FCM'],
          }),
        ),
      ),
    );
  }

  // ---------- wishlist: giảm giá, có hàng lại ----------

  wishlistAdded(eventId: string, customerId: string, productId: string) {
    return this.handle(eventId, async (tx) => {
      await tx.views.watch(customerId, productId);
      return [];
    });
  }

  wishlistRemoved(eventId: string, customerId: string, productId: string) {
    return this.handle(eventId, async (tx) => {
      await tx.views.unwatch(customerId, productId);
      return [];
    });
  }

  productUpserted(eventId: string, productId: string, name: string, price: MoneyLike | undefined) {
    return this.handle(eventId, async (tx) => {
      const priceMinor = toMinor(price);
      const before = await tx.views.updateProduct(productId, {
        name,
        ...(priceMinor !== undefined ? { priceMinor, currency: price?.currencyCode ?? 'USD' } : {}),
      });
      if (!before?.priceMinor || priceMinor === undefined || priceMinor >= before.priceMinor) return [];
      // Chỉ báo khi giảm ít nhất 5% để tránh spam vì chỉnh giá lặt vặt.
      if ((before.priceMinor - priceMinor) / before.priceMinor < 0.05) return [];
      const fmt = (minor: number) =>
        formatMoney({
          currencyCode: price?.currencyCode ?? 'USD',
          units: BigInt(Math.floor(minor / 100)),
          nanos: (minor % 100) * 1e7,
        });
      const message = templates.priceDrop(productId, name, fmt(before.priceMinor), fmt(priceMinor));
      return this.toWatchers(tx, eventId, productId, 'PRICE_DROP', message);
    });
  }

  stockChanged(eventId: string, productId: string, available: number) {
    return this.handle(eventId, async (tx) => {
      const before = await tx.views.updateProduct(productId, { available });
      if (before?.available !== 0 || available <= 0) return [];
      return this.toWatchers(
        tx,
        eventId,
        productId,
        'BACK_IN_STOCK',
        templates.backInStock(productId, before.name ?? 'Sản phẩm'),
      );
    });
  }

  lowStock(eventId: string, productId: string, available: number) {
    return this.handle(eventId, async (tx) => {
      const admins = await this.directory.withRole('ADMIN');
      return Promise.all(
        admins.map((userId) =>
          this.notifications.notifyIn(tx, {
            userId,
            category: 'INVENTORY',
            type: 'LOW_STOCK',
            message: templates.lowStock(productId, available),
            dedupKey: `${eventId}:${userId}`,
            channels: ['IN_APP', 'EMAIL'],
          }),
        ),
      );
    });
  }

  private async toWatchers(
    tx: TransactionScope,
    eventId: string,
    productId: string,
    type: string,
    message: ReturnType<typeof templates.backInStock>,
  ) {
    const watchers = await tx.views.watchers(productId);
    return Promise.all(
      watchers.map((userId) =>
        this.notifications.notifyIn(tx, {
          userId,
          category: 'WISHLIST',
          type,
          message,
          dedupKey: `${eventId}:${userId}`,
          channels: SOFT,
        }),
      ),
    );
  }
}
