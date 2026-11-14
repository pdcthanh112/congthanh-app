import { baseEnv, databaseEnv, kafkaEnv, loadEnv, redisEnv } from '@ecommerce/platform';
import { z } from 'zod';

export const envSchema = baseEnv
  .merge(databaseEnv)
  // Kafka chỉ để nhận yêu cầu xoá dữ liệu từ privacy-service (ADR 0027).
  .merge(kafkaEnv)
  .merge(redisEnv)
  .extend({
    SERVICE_NAME: z.string().default('message-service'),
    HTTP_PORT: z.coerce.number().default(5012),
    DB_SCHEMA: z.string().default('message_service'),
    PRODUCT_GRPC_ADDR: z.string().default('localhost:29090'),
    MEDIA_URL_PREFIX: z.string().default('http://localhost:9000/ecommerce-media/'),
    // Trống = tắt kiểm duyệt chat. ADR 0027.
    MODERATION_URL: z.string().default(''),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
