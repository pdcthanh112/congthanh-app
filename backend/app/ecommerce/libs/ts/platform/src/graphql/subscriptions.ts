import type { Context } from 'graphql-ws';
import type { JwtVerifier } from '../auth/jwt-verifier.js';
import { bearerToken } from '../auth/jwt-verifier.js';
import type { Principal } from '../auth/principal.js';

interface WsExtra {
  request?: { headers?: Record<string, string | string[] | undefined> };
  principal?: Principal;
}
interface WsContext {
  connectionParams?: Record<string, unknown>;
  extra?: WsExtra;
}

/**
 * Cấu hình graphql-ws cho subgraph có Subscription. Router (Cosmo) mở WebSocket tới subgraph và chuyển
 * token qua header Authorization của lần nâng cấp hoặc trong connectionParams; xác thực một lần lúc kết nối.
 */
export function graphqlWsConfig(verifier: JwtVerifier) {
  return {
    'graphql-ws': {
      path: '/graphql',
      onConnect: async (raw: Context<Record<string, unknown> | undefined, unknown>) => {
        const ctx = raw as WsContext;
        const fromParams = ctx.connectionParams?.Authorization ?? ctx.connectionParams?.authorization;
        const header = typeof fromParams === 'string' ? fromParams : ctx.extra?.request?.headers?.authorization;
        const token = bearerToken(header);
        if (!token) return false;
        try {
          // Gắn vào extra: Nest truyền extra cho hàm context của từng operation trên kết nối này.
          if (ctx.extra) ctx.extra.principal = await verifier.verify(token);
          return true;
        } catch {
          return false;
        }
      },
    },
  };
}

/** Context GraphQL dùng chung cho HTTP (req) và WebSocket (principal gắn lúc onConnect). */
export function graphqlContext({ req, extra }: { req?: unknown; extra?: WsExtra }) {
  if (req) return { req };
  // Với WS, giả lập req để @CurrentPrincipal()/AuthGuard đọc như HTTP.
  return { req: { headers: {}, principal: extra?.principal } };
}
