import { baseEnv, databaseEnv, grpcEnv, loadEnv } from '@ecommerce/platform';
import { z } from 'zod';

export const envSchema = baseEnv
  .merge(databaseEnv)
  .merge(grpcEnv)
  .extend({
    SERVICE_NAME: z.string().default('customer-service'),
    HTTP_PORT: z.coerce.number().default(5004),
    GRPC_PORT: z.coerce.number().default(19094),
    DB_SCHEMA: z.string().default('customer_service'),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
