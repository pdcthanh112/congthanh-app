import { Controller, type DynamicModule, Get, Inject, Module } from '@nestjs/common';
import {
  HealthCheck,
  HealthCheckService,
  type HealthIndicatorResult,
  HealthIndicatorService,
  TerminusModule,
} from '@nestjs/terminus';
import type pg from 'pg';
import { Public } from '../auth/decorators.js';
import { PG_POOL } from '../db/database.js';

export const HEALTH_CHECKS = Symbol('HEALTH_CHECKS');
export type HealthProbe = () => Promise<HealthIndicatorResult>;

@Controller('health')
class HealthController {
  constructor(
    private readonly health: HealthCheckService,
    @Inject(HEALTH_CHECKS) private readonly probes: HealthProbe[],
  ) {}

  /** Liveness: tiến trình còn phục vụ được; không kiểm tra phụ thuộc để tránh restart dây chuyền. */
  @Get('live')
  @Public()
  live() {
    return { status: 'ok' };
  }

  /** Readiness: phụ thuộc bắt buộc (Postgres) sẵn sàng thì mới nhận traffic. */
  @Get('ready')
  @Public()
  @HealthCheck()
  ready() {
    return this.health.check(this.probes);
  }
}

/** Probe Postgres dùng pool của service. */
export function postgresProbe(pool: pg.Pool, indicator: HealthIndicatorService): HealthProbe {
  return async () => {
    const check = indicator.check('postgres');
    try {
      await pool.query('SELECT 1');
      return check.up();
    } catch (e) {
      return check.down({ message: (e as Error).message });
    }
  };
}

@Module({})
export class PlatformHealthModule {
  /** withPostgres: thêm probe Postgres (cần provider PG_POOL). */
  static forRoot(opts: { withPostgres: boolean }): DynamicModule {
    return {
      module: PlatformHealthModule,
      imports: [TerminusModule],
      controllers: [HealthController],
      providers: [
        {
          provide: HEALTH_CHECKS,
          inject: [HealthIndicatorService, ...(opts.withPostgres ? [PG_POOL] : [])],
          useFactory: (indicator: HealthIndicatorService, pool?: pg.Pool) =>
            pool ? [postgresProbe(pool, indicator)] : [],
        },
      ],
    };
  }
}
