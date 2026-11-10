import { z } from 'zod';

/** Biến môi trường chung mọi service NestJS. Service mở rộng bằng `baseEnv.extend({...})`. */
export const baseEnv = z.object({
  NODE_ENV: z.enum(['development', 'test', 'production']).default('development'),
  SERVICE_NAME: z.string().min(1),
  HTTP_PORT: z.coerce.number().int().positive(),
  LOG_LEVEL: z.enum(['fatal', 'error', 'warn', 'info', 'debug', 'trace']).default('info'),
  LOG_FORMAT: z.enum(['json', 'pretty']).default('json'),
  OIDC_ISSUER_URI: z.url().default('http://localhost:8080/realms/ecommerce'),
  JWT_JWKS_URL: z.url().default('http://localhost:8080/realms/ecommerce/protocol/openid-connect/certs'),
  /** Audience bắt buộc trong token (bỏ trống = không kiểm tra; Keycloak mặc định aud=account). */
  JWT_AUDIENCE: z.string().optional(),
});

export const databaseEnv = z.object({
  DATABASE_URL: z.string().min(1).default('postgres://postgres:postgres@localhost:5432/ecommerce'),
  DB_SCHEMA: z.string().regex(/^[a-z_][a-z0-9_]*$/),
  DB_POOL_MAX: z.coerce.number().int().positive().default(10),
  /** true: chạy migration khi khởi động (local). Production chạy `migrate` ở init container. */
  MIGRATE_ON_START: z
    .enum(['true', 'false'])
    .default('false')
    .transform((v) => v === 'true'),
});

export const kafkaEnv = z.object({
  /** Bỏ trống = không tiêu thụ Kafka (chạy local không cần broker). */
  KAFKA_BROKERS: z
    .string()
    .default('')
    .transform((v) =>
      v
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean),
    ),
  KAFKA_CLIENT_ID: z.string().optional(),
});

export const grpcEnv = z.object({
  GRPC_PORT: z.coerce.number().int().positive(),
});

export type BaseEnv = z.infer<typeof baseEnv>;

/** Parse và kiểm tra env một lần lúc khởi động; sai cấu hình thì dừng ngay với thông báo rõ. */
export function loadEnv<T extends z.ZodType>(schema: T, source: NodeJS.ProcessEnv = process.env): z.infer<T> {
  const parsed = schema.safeParse(source);
  if (!parsed.success) {
    const issues = parsed.error.issues.map((i) => `  ${i.path.join('.')}: ${i.message}`).join('\n');
    throw new Error(`Invalid environment configuration:\n${issues}`);
  }
  return parsed.data;
}

export const valkeyEnv = z.object({
  VALKEY_URL: z.string().default('redis://localhost:6379'),
});
