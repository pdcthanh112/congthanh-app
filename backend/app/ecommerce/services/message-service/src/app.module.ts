import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  type Database,
  DB,
  graphqlWsConfig,
  JwtVerifier,
  PlatformAuthModule,
  PlatformDatabaseModule,
  PlatformHealthModule,
  PlatformLoggerModule,
  subgraphConfig,
  RedisPubSub,
} from '@ecommerce/platform';
import type { ApolloFederationDriverConfig } from '@nestjs/apollo';
import { type DynamicModule, Module } from '@nestjs/common';
import { GraphQLModule } from '@nestjs/graphql';
import { v7 as uuidv7 } from 'uuid';
import { ChatResolver } from './adapters/in/graphql/chat.resolver.js';
import { ProductRef } from './adapters/in/graphql/types.js';
import * as schema from './adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from './adapters/out/persistence/unit-of-work.js';
import { GrpcProductDirectory } from './adapters/out/product-directory.grpc.js';
import { RedisRateLimiter, RedisRealtime } from './adapters/out/realtime.js';
import { ChatService } from './application/chat.service.js';
import {
  CLOCK,
  ID_GENERATOR,
  MEDIA_PREFIX,
  PRODUCT_DIRECTORY,
  RATE_LIMITER,
  READ_REPOSITORIES,
  REALTIME,
  UNIT_OF_WORK,
} from './application/ports.js';
import type { Env } from './config/env.js';

const serviceDir = join(dirname(fileURLToPath(import.meta.url)), '..');

@Module({})
export class AppModule {
  static forRoot(env: Env): DynamicModule {
    return {
      module: AppModule,
      imports: [
        PlatformLoggerModule.forRoot({ serviceName: env.SERVICE_NAME, level: env.LOG_LEVEL, format: env.LOG_FORMAT }),
        PlatformAuthModule.forRoot({
          issuer: env.OIDC_ISSUER_URI,
          jwksUrl: env.JWT_JWKS_URL,
          audience: env.JWT_AUDIENCE,
        }),
        PlatformDatabaseModule.forRoot({
          url: env.DATABASE_URL,
          schema: env.DB_SCHEMA,
          poolMax: env.DB_POOL_MAX,
          applicationName: env.SERVICE_NAME,
          drizzleSchema: schema,
          migrateOnStart: env.MIGRATE_ON_START,
          migrationsFolder: join(serviceDir, 'drizzle'),
        }),
        PlatformHealthModule.forRoot({ withPostgres: true }),
        GraphQLModule.forRootAsync<ApolloFederationDriverConfig>({
          driver: subgraphConfig({ serviceDir, production: false }).driver,
          inject: [JwtVerifier],
          useFactory: (verifier: JwtVerifier) => {
            const { driver: _d, ...config } = subgraphConfig({
              serviceDir,
              production: env.NODE_ENV === 'production',
              extra: { subscriptions: graphqlWsConfig(verifier), buildSchemaOptions: { orphanedTypes: [ProductRef] } },
            });
            return config;
          },
        }),
      ],
      providers: [
        ChatService,
        ChatResolver,
        { provide: UNIT_OF_WORK, useClass: DrizzleUnitOfWork },
        { provide: READ_REPOSITORIES, inject: [DB], useFactory: (db: Database<typeof schema>) => readRepositories(db) },
        { provide: CLOCK, useValue: { now: () => new Date() } },
        { provide: ID_GENERATOR, useValue: { next: () => uuidv7() } },
        { provide: MEDIA_PREFIX, useValue: env.MEDIA_URL_PREFIX },
        { provide: RedisPubSub, useFactory: () => new RedisPubSub(env.REDIS_URL) },
        { provide: REALTIME, inject: [RedisPubSub], useFactory: (ps: RedisPubSub) => new RedisRealtime(ps) },
        { provide: RATE_LIMITER, useFactory: () => new RedisRateLimiter(env.REDIS_URL) },
        { provide: PRODUCT_DIRECTORY, useFactory: () => new GrpcProductDirectory(env.PRODUCT_GRPC_ADDR) },
      ],
    };
  }
}
