'use client';

/**
 * Mọi thao tác tài khoản đi qua Keycloak (OIDC): đăng nhập, đăng ký, đăng nhập mạng xã hội (identity brokering),
 * đổi mật khẩu, quên mật khẩu. Frontend không còn form mật khẩu riêng.
 */
import { signIn, signOut } from 'next-auth/react';

export type SocialProvider = 'google' | 'facebook' | 'apple';

/** Chỉ chấp nhận đường dẫn nội bộ ("/..."), chặn open redirect qua ?callbackUrl=https://evil.example. */
export function safeCallbackUrl(value: string | null | undefined, fallback = '/'): string {
  if (!value || !value.startsWith('/') || value.startsWith('//') || value.startsWith('/\\')) return fallback;
  return value;
}

const here = () => (typeof window !== 'undefined' ? window.location.pathname + window.location.search : '/');

export const login = (callbackUrl = here(), idp?: SocialProvider) =>
  signIn('keycloak', { callbackUrl }, idp ? { kc_idp_hint: idp } : undefined);

/** Mở trang đăng ký của Keycloak (OIDC prompt=create). */
export const register = (callbackUrl = here()) => signIn('keycloak', { callbackUrl }, { prompt: 'create' });

/** Keycloak yêu cầu nhập mật khẩu hiện tại rồi đặt mật khẩu mới (Application Initiated Action). */
export const changePassword = (callbackUrl = here()) =>
  signIn('keycloak', { callbackUrl }, { kc_action: 'UPDATE_PASSWORD' });

export const logout = () => signOut({ callbackUrl: '/' });

/** Sửa hồ sơ (tên, email...) bằng màn hình của Keycloak (kc_action=UPDATE_PROFILE). */
export const editProfile = (callbackUrl = here()) =>
  signIn('keycloak', { callbackUrl }, { kc_action: 'UPDATE_PROFILE' });
