import { defineConfig, devices } from '@playwright/test';

const PORT = Number(process.env.PORT ?? 3000);
const baseURL = process.env.E2E_BASE_URL ?? `http://localhost:${PORT}`;

/** E2E: chỉ dùng Playwright (Cypress đã bỏ). Unit test dùng Vitest. */
export default defineConfig({
  testDir: './tests/e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: process.env.CI ? 'github' : 'html',
  use: { baseURL, trace: 'on-first-retry' },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'mobile', use: { ...devices['Pixel 7'] } },
  ],
  // Tự chạy app nếu chưa trỏ tới môi trường có sẵn.
  webServer: process.env.E2E_BASE_URL
    ? undefined
    : { command: 'npm run start', url: baseURL, reuseExistingServer: !process.env.CI, timeout: 120_000 },
});
