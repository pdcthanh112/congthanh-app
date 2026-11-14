import { QueryClient } from '@tanstack/react-query';

export const STALE_TIME = 1000 * 60 * 5; // 5 phút

/** QueryClient dùng ở server để prefetch rồi dehydrate xuống client (HydrationBoundary). */
export const makeServerQueryClient = () =>
  new QueryClient({ defaultOptions: { queries: { staleTime: STALE_TIME } } });
