import type { DynamicModule, INestApplication, Type } from '@nestjs/common';
import { NestFactory } from '@nestjs/core';
import helmet from 'helmet';
import { Logger } from 'nestjs-pino';
import { ProblemDetailsFilter } from './errors/problem.filter.js';

export interface BootstrapOptions {
  port: number;
  /** Bật CORS (chỉ cho môi trường dev; production đi qua gateway). */
  cors?: boolean;
}

/** Khởi động service NestJS theo chuẩn chung: log pino, helmet, Problem Details, tắt êm khi SIGTERM. */
export async function bootstrap(
  module: Type<unknown> | DynamicModule,
  opts: BootstrapOptions,
): Promise<INestApplication> {
  const app = await NestFactory.create(module, { bufferLogs: true });
  app.useLogger(app.get(Logger));
  app.use(helmet({ contentSecurityPolicy: false, crossOriginEmbedderPolicy: false }));
  app.useGlobalFilters(new ProblemDetailsFilter());
  if (opts.cors) app.enableCors();
  app.enableShutdownHooks();
  await app.listen(opts.port, '0.0.0.0');
  return app;
}

/**
 * Chỉ sinh schema.graphql rồi thoát (CI dùng để compose supergraph mà không cần Postgres/Kafka).
 * Pool Postgres kết nối lười nên khởi tạo module không mở kết nối.
 */
export async function emitSchema(module: Type<unknown> | DynamicModule): Promise<void> {
  const app = await NestFactory.create(module, { logger: ['error'] });
  await app.init();
  await app.close();
}
