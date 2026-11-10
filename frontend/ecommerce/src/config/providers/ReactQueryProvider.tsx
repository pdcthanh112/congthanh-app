'use client';

import { ReactNode, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

const STALE_TIME = 1000 * 60 * 5; // 5 minutes

export const ReactQueryProvider = ({ children }: { children: ReactNode }) => {
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            staleTime: STALE_TIME,
          },
          mutations: {
            onError: (error: Error) => {
              /** use toast or notification here */
              console.error(error.message);
            },
          },
        },
      })
  );
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};
