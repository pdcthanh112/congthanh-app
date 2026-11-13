import { Logger } from '@nestjs/common';
import type pg from 'pg';

export interface JanitorOptions {
  intervalMs?: number;
  /** Giữ hàng outbox bao lâu (Debezium đọc qua WAL nên xoá không mất event); mặc định 1h. */
  outboxRetentionMs?: number;
  /** Giữ processed_events bao lâu, phải dài hơn retention của topic (7 ngày); mặc định 14 ngày. */
  processedRetentionMs?: number;
  batch?: number;
}

const HOUR = 3_600_000;

/** Dọn định kỳ outbox và processed_events quá hạn trong schema của service; bảng không có thì bỏ qua. */
export class PlatformTableJanitor {
  private readonly log = new Logger(PlatformTableJanitor.name);
  private readonly opts: Required<JanitorOptions>;
  private timer?: NodeJS.Timeout;

  constructor(
    private readonly pool: Pick<pg.Pool, 'query'>,
    opts: JanitorOptions = {},
  ) {
    this.opts = {
      intervalMs: opts.intervalMs ?? 5 * 60_000,
      outboxRetentionMs: opts.outboxRetentionMs ?? HOUR,
      processedRetentionMs: opts.processedRetentionMs ?? 14 * 24 * HOUR,
      batch: opts.batch ?? 5000,
    };
  }

  start(): void {
    this.timer = setInterval(() => void this.purgeOnce(), this.opts.intervalMs);
    this.timer.unref();
  }

  stop(): void {
    if (this.timer) clearInterval(this.timer);
  }

  async purgeOnce(now = new Date()): Promise<{ outbox: number; processedEvents: number }> {
    const result = { outbox: 0, processedEvents: 0 };
    try {
      result.outbox = await this.purge('outbox', 'created_at', new Date(now.getTime() - this.opts.outboxRetentionMs));
      result.processedEvents = await this.purge(
        'processed_events',
        'processed_at',
        new Date(now.getTime() - this.opts.processedRetentionMs),
      );
      if (result.outbox + result.processedEvents > 0) {
        this.log.log(`purged ${result.outbox} outbox rows, ${result.processedEvents} processed_events rows`);
      }
    } catch (e) {
      this.log.warn(`purge failed: ${(e as Error).message}`);
    }
    return result;
  }

  private async purge(table: 'outbox' | 'processed_events', column: string, before: Date): Promise<number> {
    const exists = await this.pool.query<{ ok: boolean }>('SELECT to_regclass($1) IS NOT NULL AS ok', [table]);
    if (!exists.rows[0]?.ok) return 0;
    // Xoá theo lô để không giữ khoá lâu và không tạo một transaction khổng lồ.
    const sql = `DELETE FROM ${table} WHERE ctid = ANY(ARRAY(SELECT ctid FROM ${table} WHERE ${column} < $1 LIMIT $2))`;
    let total = 0;
    for (;;) {
      const res = await this.pool.query(sql, [before, this.opts.batch]);
      const n = res.rowCount ?? 0;
      total += n;
      if (n < this.opts.batch) return total;
    }
  }
}
