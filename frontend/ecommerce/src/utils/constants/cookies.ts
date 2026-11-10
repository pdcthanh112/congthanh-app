export const ACCESS_TOKEN_KEY = 'accessToken';
export const REFRESH_TOKEN_KEY = 'refreshToken';
export const ROLE_KEY = 'role';
export const ACCESS_TOKEN_EXPIRED_KEY = 'accessTokenExpiredIn';

export const cookie_config = {
  path: '/',
  secure: process.env.NODE_ENV === 'production',
  sameSite: 'strict' as 'lax' | 'strict' | 'none' | undefined,
  httpOnly: true,
};
