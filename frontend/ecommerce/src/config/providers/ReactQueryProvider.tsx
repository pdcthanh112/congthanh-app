'use client';

import { ReactNode, useState } from 'react';
import { MutationCache, QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { useErrorMessage } from '@/hooks/useErrorMessage';

import { STALE_TIME } from '@/lib/query-client';

export const ReactQueryProvider = ({ children }: { children: ReactNode }) => {
  const message = useErrorMessage();
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: { refetchOnWindowFocus: false, staleTime: STALE_TIME },
        },
        mutationCache: new MutationCache({
          // Mutation không tự xử lý lỗi thì hiện thông báo chung đã dịch.
          onError: (error, _vars, _ctx, mutation) => {
            if (!mutation.options.onError) toast.error(message(error));
          },
        }),
      })
  );
  return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
};
