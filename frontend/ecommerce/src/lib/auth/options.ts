import type { NextAuthOptions } from 'next-auth';
import type { JWT } from 'next-auth/jwt';
import KeycloakProvider from 'next-auth/providers/keycloak';
import { REFRESH_MARGIN_SECONDS } from './constants';

const issuer = process.env.KEYCLOAK_ISSUER ?? 'http://localhost:8180/realms/ecommerce';

const clientId = process.env.KEYCLOAK_CLIENT_ID ?? 'storefront-web';
const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET ?? '';

function rolesOf(accessToken: string | undefined): string[] {
  if (!accessToken) return [];
  try {
    const claims = JSON.parse(Buffer.from(accessToken.split('.')[1], 'base64url').toString('utf8'));
    const realm: string[] = claims.realm_access?.roles ?? [];
    const client: string[] = claims.resource_access?.[clientId]?.roles ?? [];
    const flat: string[] = Array.isArray(claims.roles) ? claims.roles : []; // client scope roles-claim
    return [...new Set([...realm, ...client, ...flat].map((r) => r.toUpperCase().replace(/^ROLE_/, '')))];
  } catch {
    return [];
  }
}

async function refreshAccessToken(token: JWT): Promise<JWT> {
  try {
    const res = await fetch(`${issuer}/protocol/openid-connect/token`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({
        grant_type: 'refresh_token',
        client_id: clientId,
        ...(clientSecret ? { client_secret: clientSecret } : {}),
        refresh_token: token.refreshToken ?? '',
      }),
    });
    const t = await res.json();
    if (!res.ok) throw t;
    return {
      ...token,
      accessToken: t.access_token,
      idToken: t.id_token ?? token.idToken,
      refreshToken: t.refresh_token ?? token.refreshToken,
      expiresAt: Math.floor(Date.now() / 1000) + Number(t.expires_in ?? 300),
      roles: rolesOf(t.access_token),
      error: undefined,
    };
  } catch {
    return { ...token, error: 'RefreshAccessTokenError' };
  }
}

export const authOptions: NextAuthOptions = {
  providers: [
    KeycloakProvider({
      clientId,
      clientSecret,
      issuer,
      checks: ['pkce', 'state'],
      ...(clientSecret ? {} : { client: { token_endpoint_auth_method: 'none' } }),
    }),
  ],
  pages: { signIn: '/auth/login' },
  session: { strategy: 'jwt', maxAge: 30 * 24 * 60 * 60 },
  callbacks: {
    async jwt({ token, account }) {
      if (account) {
        return {
          ...token,
          accessToken: account.access_token,
          refreshToken: account.refresh_token,
          idToken: account.id_token,
          expiresAt: account.expires_at,
          roles: rolesOf(account.access_token),
        };
      }
      // Làm mới sớm 60 giây trước khi hết hạn; SessionProvider gọi lại /api/auth/session định kỳ (SESSION_REFETCH_SECONDS)
      // nên cookie luôn mang token còn hạn khi BFF đọc.
      if (token.expiresAt && Date.now() / 1000 > token.expiresAt - REFRESH_MARGIN_SECONDS)
        return refreshAccessToken(token);
      return token;
    },
    async session({ session, token }) {
      const sub = token.sub ?? '';
      session.user = { ...session.user, id: sub, accountId: sub, role: token.roles ?? [] };
      // Access token không đưa ra trình duyệt: GraphQL/REST đi qua /api/graphql và /api/gateway (BFF),
      // token chỉ nằm trong cookie JWT httpOnly của NextAuth.
      session.error = token.error;
      return session;
    },
  },
  events: {
    // Đăng xuất cả phiên SSO trên Keycloak, không chỉ cookie NextAuth.
    async signOut({ token }) {
      if (!token?.idToken) return;
      const url = new URL(`${issuer}/protocol/openid-connect/logout`);
      url.searchParams.set('id_token_hint', token.idToken);
      await fetch(url).catch(() => undefined);
    },
  },
  secret: process.env.NEXTAUTH_SECRET,
};
