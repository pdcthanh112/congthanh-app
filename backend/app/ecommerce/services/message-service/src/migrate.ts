import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { createPool, runMigrations } from '@ecommerce/platform';
import { env } from './config/env.js';

// Chạy ở init container trước khi pod nhận traffic: node dist/migrate.js
const config = env();
const pool = createPool({
  url: config.DATABASE_URL,
  schema: config.DB_SCHEMA,
  poolMax: 1,
  applicationName: `${config.SERVICE_NAME}-migrate`,
});
try {
  await runMigrations(pool, config.DB_SCHEMA, join(dirname(fileURLToPath(import.meta.url)), '..', 'drizzle'));
} finally {
  await pool.end();
}
