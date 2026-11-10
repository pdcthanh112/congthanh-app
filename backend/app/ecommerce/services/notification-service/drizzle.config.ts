import { defineConfig } from 'drizzle-kit';

// npm run db:generate -w @ecom/notification-service  → sinh migration SQL vào drizzle/ từ schema.ts (commit cả hai).
export default defineConfig({
  dialect: 'postgresql',
  schema: './src/adapters/out/persistence/schema.ts',
  out: './drizzle',
  casing: 'snake_case',
  schemaFilter: ['notification_service'],
});
