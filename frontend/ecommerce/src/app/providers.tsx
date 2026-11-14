'use client';

import React from 'react';
import { SessionProvider } from 'next-auth/react';
import { ReactQueryProvider, ThemeProvider } from '@/config/providers';
import SessionGuard from '@/features/auth/SessionGuard';
import { SESSION_REFETCH_SECONDS } from '@/lib/auth/constants';

const Providers = ({ children }: { children: React.ReactNode }) => (
  <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
    {/* Gọi lại session định kỳ để NextAuth làm mới access token trong cookie trước khi hết hạn. */}
    <SessionProvider refetchInterval={SESSION_REFETCH_SECONDS}>
      <SessionGuard />
      <ReactQueryProvider>{children}</ReactQueryProvider>
    </SessionProvider>
  </ThemeProvider>
);

export default Providers;
