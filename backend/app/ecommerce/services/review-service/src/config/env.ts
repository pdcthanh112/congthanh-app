import { baseEnv, databaseEnv, kafkaEnv, loadEnv } from '@ecom/platform';
import { z } from 'zod';

export const envSchema = baseEnv
  .merge(databaseEnv)
  .merge(kafkaEnv)
  .extend({
    SERVICE_NAME: z.string().default('review-service'),
    HTTP_PORT: z.coerce.number().default(5010),
    DB_SCHEMA: z.string().default('review_service'),
    PRODUCT_GRPC_ADDR: z.string().default('localhost:29090'),
    /** URL gốc của file do file-service cấp; ảnh/video đánh giá phải bắt đầu bằng chuỗi này. */
    MEDIA_URL_PREFIX: z.string().default('http://localhost:9000/ecommerce-media/'),
  });
export type Env = z.infer<typeof envSchema>;
export const env = (): Env => loadEnv(envSchema);
