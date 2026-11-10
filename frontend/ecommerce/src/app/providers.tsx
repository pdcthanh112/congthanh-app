'use client';

import React from 'react';
import { LayoutProps } from './[locale]/page';
import { ApolloClient, ApolloProvider, InMemoryCache } from '@apollo/client';
import { SessionProvider } from 'next-auth/react';
import { ReactQueryProvider, ReduxStoreProvider, ThemeProvider } from '@/config/providers';
import { AuthContextProvider } from '@/context/AuthContext';
import AuthWrapper from '@/components/AuthWrapper';
import { AntdRegistry } from '@ant-design/nextjs-registry';

const Providers = ({ children }: Readonly<LayoutProps>) => {
  const graphClient = new ApolloClient({
    uri: `${process.env.REACT_APP_API_URL}/graphql`,
    cache: new InMemoryCache(),
  });

  return (
    <AuthContextProvider>
      {/* <AuthWrapper> */}
        <ThemeProvider attribute="class" defaultTheme="system" enableSystem disableTransitionOnChange>
          <SessionProvider>
            <ApolloProvider client={graphClient}>
              <ReduxStoreProvider>
                <ReactQueryProvider>
                <AntdRegistry>{children}</AntdRegistry>
                  </ReactQueryProvider>
              </ReduxStoreProvider>
            </ApolloProvider>
          </SessionProvider>
        </ThemeProvider>
      {/* </AuthWrapper> */}
    </AuthContextProvider>
  );
};

export default Providers;
