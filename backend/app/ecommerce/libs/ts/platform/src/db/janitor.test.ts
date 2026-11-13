import pg from 'pg';
import { afterAll, beforeAll, describe, expect, it } from 'vitest';
import { PlatformTableJanitor } from './janitor.js';

const url = process.env.TEST_DATABASE_URL;

describe.skipIf(!url)('PlatformTableJanitor', () => {
  const schema = `janitor_ts_${Date.now()}`;
  let pool: pg.Pool;

  beforeAll(async () => {
    pool = new pg.Pool({ connectionString: url, max: 1, options: `-c search_path=${schema}` });
    await pool.query(`CREATE SCHEMA ${schema}`);
    await pool.query(
      `CREATE TABLE ${schema}.outbox (id uuid PRIMARY KEY, created_at timestamptz NOT NULL DEFAULT now())`,
    );
    await pool.query(
      `CREATE TABLE ${schema}.processed_events (event_id text, consumer text, processed_at timestamptz NOT NULL DEFAULT now(),
       PRIMARY KEY (event_id, consumer))`,
    );
  });

  afterAll(async () => {
    await pool?.query(`DROP SCHEMA ${schema} CASCADE`);
    await pool?.end();
  });

  it('chỉ xoá hàng quá hạn, theo lô', async () => {
    await pool.query(
      `INSERT INTO outbox (id, created_at) SELECT gen_random_uuid(), now() - interval '2 hours' FROM generate_series(1, 5)`,
    );
    await pool.query(`INSERT INTO outbox (id) VALUES (gen_random_uuid())`);
    await pool.query(
      `INSERT INTO processed_events VALUES ('old', 'g', now() - interval '20 days'), ('new', 'g', now())`,
    );

    const purged = await new PlatformTableJanitor(pool, { batch: 2 }).purgeOnce();

    expect(purged).toEqual({ outbox: 5, processedEvents: 1 });
    const left = await pool.query('SELECT count(*)::int AS n FROM outbox');
    expect(left.rows[0].n).toBe(1);
  });
});
