import { baseEnv, databaseEnv, loadEnv, valkeyEnv } from '@ecom/platform';
import { z } from 'zod';

export const envSchema = baseEnv
  .merge(databaseEnv)
  .merge(valkeyEnv)
  .extend({
    SERVICE_NAME: z.string().default('message-service'),
    HTTP_PORT: z.coerce.number().default(5012),
    DB_SCHEMA: z.string().default('message_service'),
    PRODUCT_GRPC_ADDR: z.string().default('localhost:29090'),
    MEDIA_URL_PREFIX: z.string().default('http://localhost:9000/ecommerce-media/'),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
