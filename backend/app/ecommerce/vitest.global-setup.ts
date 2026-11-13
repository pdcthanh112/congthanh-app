/**
 * Postgres cho test tích hợp: có TEST_DATABASE_URL (CI service container, `make up`) thì dùng luôn; không thì bật
 * một container bằng Testcontainers cho cả lượt chạy. Worker (pool forks) kế thừa biến môi trường đặt ở đây.
 * Không có Docker: local thì test tích hợp tự skip, trên CI thì fail để không bị bỏ qua trong im lặng.
 */
const POSTGRES_IMAGE = 'pgvector/pgvector:0.8.1-pg18-trixie'; // cùng image với CI và docker-compose

export default async function setup(): Promise<(() => Promise<void>) | undefined> {
  if (process.env.TEST_DATABASE_URL) return undefined;
  try {
    const { PostgreSqlContainer } = await import('@testcontainers/postgresql');
    const pg = await new PostgreSqlContainer(POSTGRES_IMAGE)
      .withDatabase('test')
      .withUsername('postgres')
      .withPassword('postgres')
      .start();
    process.env.TEST_DATABASE_URL = pg.getConnectionUri();
    return async () => {
      await pg.stop();
    };
  } catch (e) {
    if (process.env.CI) {
      throw new Error(`TEST_DATABASE_URL not set and Testcontainers failed: ${(e as Error).message}`);
    }
    console.warn(`[vitest] no TEST_DATABASE_URL and no test container (is Docker running?), integration tests skip`);
    return undefined;
  }
}
