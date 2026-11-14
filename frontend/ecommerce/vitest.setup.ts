import '@testing-library/jest-dom/vitest';
import { vi } from 'vitest';

// `server-only` ném lỗi khi bị import ngoài React Server Components; trong test thì bỏ qua.
vi.mock('server-only', () => ({}));
