import type { Database } from "./database.js";
import type { PlatformTables } from "./platform-tables.js";

export async function markProcessed(tx: Pick<Database, "insert">, table: PlatformTables["processedEvents"], eventId: string, consumer: string): Promise<boolean> {
  const rows = await tx.insert(table).values({ eventId, consumer }).onConflictDoNothing().returning({ eventId: table.eventId });
  return rows.length > 0;
}
