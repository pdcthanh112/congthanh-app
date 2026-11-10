import type { Database } from './database.js';
import type { PlatformTables } from './platform-tables.js';

/**
 * Đánh dấu event đã xử lý trong transaction hiện tại. Trả false nếu đã xử lý trước đó (Kafka giao lại
 * sau rebalance/restart): caller bỏ qua tác dụng phụ, transaction vẫn commit để offset được ghi nhận.
 */
export async function markProcessed(
  tx: Pick<Database, 'insert'>,
  table: PlatformTables['processedEvents'],
  eventId: string,
  consumer: string,
): Promise<boolean> {
  const rows = await tx
    .insert(table)
    .values({ eventId, consumer })
    .onConflictDoNothing()
    .returning({ eventId: table.eventId });
  return rows.length > 0;
}
