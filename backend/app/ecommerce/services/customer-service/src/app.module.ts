import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  type Database,
  DB,
  GrpcServer,
  PlatformAuthModule,
  PlatformDatabaseModule,
  PlatformHealthModule,
  PlatformLoggerModule,
  subgraphConfig,
} from '@ecom/platform';
import { type DynamicModule, Module } from '@nestjs/common';
import { GraphQLModule } from '@nestjs/graphql';
import { v7 as uuidv7 } from 'uuid';
import { CustomerResolver, ProductWishlistResolver } from './adapters/in/graphql/customer.resolver.js';
import { ProductRef } from './adapters/in/graphql/types.js';
import { customerGrpcRoutes } from './adapters/in/grpc/customer.grpc.js';
import * as schema from './adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from './adapters/out/persistence/unit-of-work.js';
import { CustomerService } from './application/customer.service.js';
import { CLOCK, ID_GENERATOR, READ_REPOSITORIES, UNIT_OF_WORK } from './application/ports.js';
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
        GraphQLModule.forRoot(
          subgraphConfig({
            serviceDir,
            production: env.NODE_ENV === 'production',
            extra: { buildSchemaOptions: { orphanedTypes: [ProductRef] } },
          }),
        ),
      ],
      providers: [
        CustomerService,
        CustomerResolver,
        ProductWishlistResolver,
        { provide: UNIT_OF_WORK, useClass: DrizzleUnitOfWork },
        { provide: READ_REPOSITORIES, inject: [DB], useFactory: (db: Database<typeof schema>) => readRepositories(db) },
        { provide: CLOCK, useValue: { now: () => new Date() } },
        { provide: ID_GENERATOR, useValue: { next: () => uuidv7() } },
        {
          provide: GrpcServer,
          inject: [CustomerService],
          useFactory: (customers: CustomerService) => new GrpcServer(env.GRPC_PORT, customerGrpcRoutes(customers)),
        },
      ],
    };
  }
}
