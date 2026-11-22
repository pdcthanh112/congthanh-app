import { Logger } from "@nestjs/common";
import { sql } from "drizzle-orm";
import { drizzle, type NodePgDatabase } from "drizzle-orm/node-postgres";
import { migrate } from "drizzle-orm/node-postgres/migrator";
import pg from "pg";

export type Database<S extends Record<string, unknown> = any> = NodePgDatabase<S>;

export type Tx<S extends Record<string, unknown> = Record<string, unknown>> = Parameters<Parameters<Database<S>["transaction"]>[0]>[0];

export const DB = Symbol("DB");
export const PG_POOL = Symbol("PG_POOL");

export interface DatabaseOptions {
  url: string;
  schema: string;
  poolMax: number;
  applicationName: string;
}

export function createPool(opts: DatabaseOptions): pg.Pool {
  return new pg.Pool({
    connectionString: opts.url,
    max: opts.poolMax,
    application_name: opts.applicationName,
    options: `-c search_path=${opts.schema},public`,
    idleTimeoutMillis: 30_000,
    connectionTimeoutMillis: 5_000,
  });
}

export async function runMigrations(pool: pg.Pool, schema: string, folder: string): Promise<void> {
  const log = new Logger("Migrations");

  const client = await pool.connect();
  try {
    await client.query("SELECT pg_advisory_lock(hashtext($1))", [`migrate:${schema}`]);
    try {
      const db = drizzle(client);
      await db.execute(sql.raw(`CREATE SCHEMA IF NOT EXISTS "${schema}"`));
      await migrate(db, {
        migrationsFolder: folder,
        migrationsSchema: schema,
        migrationsTable: "__drizzle_migrations",
      });
    } finally {
      await client.query("SELECT pg_advisory_unlock(hashtext($1))", [`migrate:${schema}`]);
    }
  } finally {
    client.release();
  }
  log.log(`schema ${schema} is up to date`);
}
