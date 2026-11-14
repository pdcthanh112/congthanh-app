import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';
import tsconfigPaths from 'vite-tsconfig-paths';

/** Unit/component test. E2E chạy bằng Playwright (tests/e2e). */
export default defineConfig({
  plugins: [tsconfigPaths(), react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./vitest.setup.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
    css: false,
    // next-intl import 'next/navigation' không có phần mở rộng: để Vite xử lý thay vì Node ESM.
    server: { deps: { inline: ['next-intl'] } },
    coverage: { provider: 'v8', include: ['src/**/*.{ts,tsx}'] },
  },
});
