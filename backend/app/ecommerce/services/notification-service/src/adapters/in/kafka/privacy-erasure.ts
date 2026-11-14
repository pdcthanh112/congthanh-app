import { type Database, type ErasureTarget, privacyErasureConsumer, type Tx } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { outbox, processedEvents } from '../../out/persistence/schema.js';

/** Xoá dữ liệu của khách khi privacy-service yêu cầu (CCPA/CPRA). */
export const erasureTarget: ErasureTarget = {
  processor: 'notification-service',
  async erase(tx: Tx, subjectId: string): Promise<string | null> {
    await tx.execute(sql`DELETE FROM notification_service.deliveries WHERE notification_id IN
      (SELECT id FROM notification_service.notifications WHERE user_id = ${subjectId})`);
    await tx.execute(sql`DELETE FROM notification_service.notifications WHERE user_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM notification_service.fcm_tokens WHERE user_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM notification_service.web_push_subscriptions WHERE user_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM notification_service.preferences WHERE user_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM notification_service.product_watchers WHERE customer_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM notification_service.order_customers WHERE customer_id = ${subjectId}`);
    return null;
  },
};

export function erasureConsumer(db: Database, brokers: string[], clientId: string) {
  return privacyErasureConsumer({
    db,
    tables: { outbox, processedEvents },
    source: 'notification-service',
    target: erasureTarget,
    brokers,
    clientId,
  });
}
