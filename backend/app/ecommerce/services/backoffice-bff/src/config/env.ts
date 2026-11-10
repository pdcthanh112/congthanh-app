import { baseEnv, loadEnv } from '@ecom/platform';
import { z } from 'zod';

export const envSchema = baseEnv.extend({
  SERVICE_NAME: z.string().default('backoffice-bff'),
  HTTP_PORT: z.coerce.number().default(5021),
  CURRENCY: z.string().length(3).default('VND'),
  PRODUCT_GRPC_ADDR: z.string().default('localhost:29090'),
  PRODUCT_REST_URL: z.url().default('http://localhost:5008'),
  CART_GRPC_ADDR: z.string().default('localhost:19091'),
  CART_REST_URL: z.url().default('http://localhost:5002'),
  INVENTORY_GRPC_ADDR: z.string().default('localhost:9092'),
  PROMOTION_GRPC_ADDR: z.string().default('localhost:9095'),
  ORDER_REST_URL: z.url().default('http://localhost:5006'),
});
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
