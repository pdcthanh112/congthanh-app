import 'server-only';
import { registerServerTokenProvider } from '@/graphql/client';
import { serverAccessToken } from './server-token';

/**
 * Import (side effect) ở root layout và các route handler: cho gql()/axios phía server biết cách đọc access token
 * từ cookie NextAuth, mà không kéo next/headers vào bundle client (graphql/client.ts dùng chung cho cả hai phía).
 */
registerServerTokenProvider(serverAccessToken);
