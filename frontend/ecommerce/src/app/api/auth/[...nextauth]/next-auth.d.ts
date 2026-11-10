import { DefaultSession } from 'next-auth';

declare module 'next-auth' {
  interface Session {
    user: {
      /** Keycloak subject (sub), dùng làm id khách hàng ở mọi service. */
      id: string;
      accountId: string;
      role: string[];
    } & DefaultSession['user'];
    /** Access token Keycloak gửi kèm mọi request GraphQL/REST (Authorization: Bearer). */
    accessToken?: string;
    error?: 'RefreshAccessTokenError';
    token?: { accessToken: string } & DefaultSession['user'];
  }
}

declare module 'next-auth/jwt' {
  interface JWT {
    sub?: string;
    roles?: string[];
    accessToken?: string;
    refreshToken?: string;
    idToken?: string;
    expiresAt?: number;
    error?: 'RefreshAccessTokenError';
  }
}
