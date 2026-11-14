import { type Database, type ErasureTarget, privacyErasureConsumer, type Tx } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { outbox, processedEvents } from '../../out/persistence/schema.js';

/** Xoá dữ liệu của khách khi privacy-service yêu cầu (CCPA/CPRA). */
export const erasureTarget: ErasureTarget = {
  processor: 'customer-service',
  async erase(tx: Tx, subjectId: string): Promise<string | null> {
    // Địa chỉ, wishlist, lịch sử tìm kiếm: xoá hẳn.
    await tx.execute(sql`DELETE FROM customer_service.search_history WHERE user_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM customer_service.wishlist_items WHERE customer_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM customer_service.shipping_addresses WHERE customer_id = ${subjectId}`);
    return null;
  },
};

export function erasureConsumer(db: Database, brokers: string[], clientId: string) {
  return privacyErasureConsumer({
    db,
    tables: { outbox, processedEvents },
    source: 'customer-service',
    target: erasureTarget,
    brokers,
    clientId,
  });
}
