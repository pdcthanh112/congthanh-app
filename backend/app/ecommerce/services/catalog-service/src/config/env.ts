import { baseEnv, databaseEnv, grpcEnv, loadEnv } from '@ecommerce/platform';
import { z } from 'zod';

export const envSchema = baseEnv
  .merge(databaseEnv)
  .merge(grpcEnv)
  .extend({
    SERVICE_NAME: z.string().default('catalog-service'),
    HTTP_PORT: z.coerce.number().default(5003),
    GRPC_PORT: z.coerce.number().default(19092),
    DB_SCHEMA: z.string().default('catalog_service'),
    PRODUCT_GRPC_ADDR: z.string().default('localhost:29090'),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
