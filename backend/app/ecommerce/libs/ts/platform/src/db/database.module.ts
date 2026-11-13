import { type DynamicModule, Global, Inject, Module, type OnApplicationShutdown } from '@nestjs/common';
import { drizzle } from 'drizzle-orm/node-postgres';
import type pg from 'pg';
import { createPool, type DatabaseOptions, DB, PG_POOL, runMigrations } from './database.js';
import { type JanitorOptions, PlatformTableJanitor } from './janitor.js';

export interface DatabaseModuleOptions extends DatabaseOptions {
  // biome-ignore lint/suspicious/noExplicitAny: schema Drizzle của service
  drizzleSchema: Record<string, any>;
  migrateOnStart: boolean;
  migrationsFolder: string;
  /** Dọn outbox/processed_events quá hạn; false để tắt. */
  janitor?: JanitorOptions | false;
}

@Global()
@Module({})
export class PlatformDatabaseModule implements OnApplicationShutdown {
  constructor(
    @Inject(PG_POOL) private readonly pool: pg.Pool,
    @Inject(PlatformTableJanitor) private readonly janitor: PlatformTableJanitor | null,
  ) {}

  static forRoot(opts: DatabaseModuleOptions): DynamicModule {
    return {
      module: PlatformDatabaseModule,
      providers: [
        {
          provide: PG_POOL,
          useFactory: async () => {
            const pool = createPool(opts);
            if (opts.migrateOnStart) {
              await runMigrations(pool, opts.schema, opts.migrationsFolder);
            }
            return pool;
          },
        },
        {
          provide: PlatformTableJanitor,
          inject: [PG_POOL],
          useFactory: (pool: pg.Pool) => {
            if (opts.janitor === false) return null;
            const janitor = new PlatformTableJanitor(pool, opts.janitor);
            janitor.start();
            return janitor;
          },
        },
        {
          provide: DB,
          inject: [PG_POOL],
          useFactory: (pool: pg.Pool) => drizzle(pool, { schema: opts.drizzleSchema, casing: 'snake_case' }),
        },
      ],
      exports: [PG_POOL, DB],
    };
  }

  async onApplicationShutdown(): Promise<void> {
    this.janitor?.stop();
    await this.pool.end();
  }
}
