import { type Database, type ErasureTarget, erasedId, privacyErasureConsumer, type Tx } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { outbox, processedEvents } from '../../out/persistence/schema.js';

/** Xoá dữ liệu của khách khi privacy-service yêu cầu (CCPA/CPRA). */
export const erasureTarget: ErasureTarget = {
  processor: 'review-service',
  async erase(tx: Tx, subjectId: string): Promise<string | null> {
    // Đánh giá là nội dung công khai gắn với sản phẩm: ẩn danh tác giả (tên, id) thay vì xoá để điểm trung bình
    // không đổi; phiếu "hữu ích" và lịch sử mua thì xoá/ẩn danh.
    const anon = erasedId(subjectId);
    await tx.execute(sql`UPDATE review_service.reviews SET author_name = 'Former customer', customer_id = ${anon}
      WHERE customer_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM review_service.helpful_votes WHERE user_id = ${subjectId}`);
    await tx.execute(sql`UPDATE review_service.purchases SET customer_id = ${anon} WHERE customer_id = ${subjectId}`);
    return null;
  },
};

export function erasureConsumer(db: Database, brokers: string[], clientId: string) {
  return privacyErasureConsumer({
    db,
    tables: { outbox, processedEvents },
    source: 'review-service',
    target: erasureTarget,
    brokers,
    clientId,
  });
}
