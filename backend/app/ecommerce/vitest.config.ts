import swc from 'unplugin-swc';
import { defineConfig } from 'vitest/config';

// SWC thay esbuild để có emitDecoratorMetadata: NestJS DI cần metadata kiểu tham số constructor.
export default defineConfig({
  plugins: [
    swc.vite({
      jsc: {
        parser: { syntax: 'typescript', decorators: true },
        transform: { legacyDecorator: true, decoratorMetadata: true },
        target: 'es2023',
      },
    }),
  ],
  resolve: { conditions: ['source'] },
  ssr: { resolve: { conditions: ['source'] } },
  test: {
    include: ['libs/ts/*/src/**/*.test.ts', 'services/*/src/**/*.test.ts', 'services/*/test/**/*.test.ts'],
    environment: 'node',
    pool: 'forks',
  },
});
