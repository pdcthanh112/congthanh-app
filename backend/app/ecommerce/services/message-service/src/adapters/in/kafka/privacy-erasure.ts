import { type Database, type ErasureTarget, erasedId, privacyErasureConsumer, type Tx } from '@ecommerce/platform';
import { sql } from 'drizzle-orm';
import { outbox, processedEvents } from '../../out/persistence/schema.js';

/** Xoá dữ liệu của khách khi privacy-service yêu cầu (CCPA/CPRA). */
export const erasureTarget: ErasureTarget = {
  processor: 'message-service',
  async erase(tx: Tx, subjectId: string): Promise<string | null> {
    // Tin nhắn của khách: xoá nội dung và tệp đính kèm; hội thoại giữ lại (người bán/CSKH vẫn cần lịch sử của họ)
    // nhưng bỏ liên kết tới khách.
    const anon = erasedId(subjectId);
    await tx.execute(sql`UPDATE message_service.messages SET body = '[deleted]', attachments = '{}'::text[],
      sender_id = ${anon} WHERE sender_id = ${subjectId}`);
    await tx.execute(sql`UPDATE message_service.conversations SET customer_id = ${anon}, last_message_preview = ''
      WHERE customer_id = ${subjectId}`);
    await tx.execute(sql`DELETE FROM message_service.conversation_reads WHERE user_id = ${subjectId}`);
    return null;
  },
};

export function erasureConsumer(db: Database, brokers: string[], clientId: string) {
  return privacyErasureConsumer({
    db,
    tables: { outbox, processedEvents },
    source: 'message-service',
    target: erasureTarget,
    brokers,
    clientId,
  });
}
