import { DefaultSession } from 'next-auth';

declare module 'next-auth' {
  interface Session {
    user: {
      /** Keycloak subject (sub), dùng làm id khách hàng ở mọi service. */
      id: string;
      accountId: string;
      role: string[];
    } & DefaultSession['user'];
    /** Làm mới token thất bại: client phải đăng nhập lại (xem SessionGuard). */
    error?: 'RefreshAccessTokenError';
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
