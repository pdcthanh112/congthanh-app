import { baseEnv, databaseEnv, kafkaEnv, loadEnv, valkeyEnv } from '@ecom/platform';
import { z } from 'zod';

const optional = z
  .string()
  .optional()
  .transform((v) => v || undefined);

export const envSchema = baseEnv
  .merge(databaseEnv)
  .merge(kafkaEnv)
  .merge(valkeyEnv)
  .extend({
    SERVICE_NAME: z.string().default('notification-service'),
    HTTP_PORT: z.coerce.number().default(5011),
    DB_SCHEMA: z.string().default('notification_service'),
    APP_URL: z.string().default('http://localhost:3000'),
    // Keycloak Admin API (service account notification-service, role view-users)
    KEYCLOAK_URL: z.string().default('http://localhost:8180'),
    KEYCLOAK_REALM: z.string().default('ecommerce'),
    KEYCLOAK_CLIENT_ID: z.string().default('notification-service'),
    KEYCLOAK_CLIENT_SECRET: z.string().default(''),
    // Kênh: thiếu cấu hình thì kênh đó tắt (lượt gửi bị SKIPPED)
    SMTP_URL: optional,
    MAIL_FROM: z.string().default('CongThanh <no-reply@congthanh.vn>'),
    SMS_WEBHOOK_URL: optional,
    SMS_API_KEY: optional,
    SMS_ENABLED: z
      .enum(['true', 'false'])
      .default('true')
      .transform((v) => v === 'true'),
    VAPID_SUBJECT: z.string().default('mailto:admin@congthanh.vn'),
    VAPID_PUBLIC_KEY: optional,
    VAPID_PRIVATE_KEY: optional,
    FCM_SERVICE_ACCOUNT_JSON: optional,
    DELIVERY_WORKER_ENABLED: z
      .enum(['true', 'false'])
      .default('true')
      .transform((v) => v === 'true'),
    DELIVERY_INTERVAL_MS: z.coerce.number().default(2000),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
