import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
  type Database,
  DB,
  graphqlWsConfig,
  JwtVerifier,
  KafkaEventConsumer,
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
import { NotificationResolver } from './adapters/in/graphql/notification.resolver.js';
import { domainEventsConsumer } from './adapters/in/kafka/domain-events.consumer.js';
import { EmailSender } from './adapters/out/channels/email.sender.js';
import { FcmSender } from './adapters/out/channels/fcm.sender.js';
import { SmsSender } from './adapters/out/channels/sms.sender.js';
import { WebPushSender } from './adapters/out/channels/web-push.sender.js';
import { KeycloakRecipientDirectory } from './adapters/out/keycloak-directory.js';
import * as schema from './adapters/out/persistence/schema.js';
import { DrizzleUnitOfWork, readRepositories } from './adapters/out/persistence/unit-of-work.js';
import { RedisRealtimePublisher } from './adapters/out/realtime.publisher.js';
import { DeliveryWorker, WORKER_OPTIONS } from './application/delivery.worker.js';
import { EventHandlers } from './application/event-handlers.js';
import { NotificationService } from './application/notification.service.js';
import {
  CHANNEL_SENDERS,
  type ChannelSender,
  CLOCK,
  ID_GENERATOR,
  READ_REPOSITORIES,
  REALTIME,
  RECIPIENT_DIRECTORY,
  UNIT_OF_WORK,
} from './application/ports.js';
import type { Env } from './config/env.js';

const serviceDir = join(dirname(fileURLToPath(import.meta.url)), '..');

/** Chỉ bật kênh có cấu hình; thiếu thì lượt gửi kênh đó bị SKIPPED (thấy rõ trong bảng deliveries). */
function senders(env: Env): ChannelSender[] {
  const list: ChannelSender[] = [];
  if (env.SMTP_URL) list.push(new EmailSender({ url: env.SMTP_URL, from: env.MAIL_FROM, appUrl: env.APP_URL }));
  if (env.SMS_ENABLED) list.push(new SmsSender({ webhookUrl: env.SMS_WEBHOOK_URL, apiKey: env.SMS_API_KEY }));
  if (env.VAPID_PUBLIC_KEY && env.VAPID_PRIVATE_KEY) {
    list.push(
      new WebPushSender({
        subject: env.VAPID_SUBJECT,
        publicKey: env.VAPID_PUBLIC_KEY,
        privateKey: env.VAPID_PRIVATE_KEY,
      }),
    );
  }
  if (env.FCM_SERVICE_ACCOUNT_JSON) list.push(new FcmSender(env.FCM_SERVICE_ACCOUNT_JSON));
  return list;
}

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
              extra: { subscriptions: graphqlWsConfig(verifier) },
            });
            return config;
          },
        }),
      ],
      providers: [
        NotificationService,
        EventHandlers,
        DeliveryWorker,
        NotificationResolver,
        { provide: UNIT_OF_WORK, useClass: DrizzleUnitOfWork },
        { provide: READ_REPOSITORIES, inject: [DB], useFactory: (db: Database<typeof schema>) => readRepositories(db) },
        { provide: CLOCK, useValue: { now: () => new Date() } },
        { provide: ID_GENERATOR, useValue: { next: () => uuidv7() } },
        { provide: RedisPubSub, useFactory: () => new RedisPubSub(env.REDIS_URL) },
        {
          provide: REALTIME,
          inject: [RedisPubSub],
          useFactory: (ps: RedisPubSub) => new RedisRealtimePublisher(ps),
        },
        {
          provide: RECIPIENT_DIRECTORY,
          useFactory: () =>
            new KeycloakRecipientDirectory({
              baseUrl: env.KEYCLOAK_URL,
              realm: env.KEYCLOAK_REALM,
              clientId: env.KEYCLOAK_CLIENT_ID,
              clientSecret: env.KEYCLOAK_CLIENT_SECRET,
            }),
        },
        { provide: CHANNEL_SENDERS, useFactory: () => senders(env) },
        {
          provide: WORKER_OPTIONS,
          useValue: { enabled: env.DELIVERY_WORKER_ENABLED, intervalMs: env.DELIVERY_INTERVAL_MS, batchSize: 50 },
        },
        {
          provide: KafkaEventConsumer,
          inject: [EventHandlers],
          useFactory: (h: EventHandlers) =>
            domainEventsConsumer(h, env.KAFKA_BROKERS, env.KAFKA_CLIENT_ID ?? env.SERVICE_NAME),
        },
      ],
    };
  }
}
