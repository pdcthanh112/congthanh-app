/**
 * Tài khoản do Keycloak quản lý: các hàm cũ giữ tên để saga/form hiện có gọi được, nhưng chuyển trang sang
 * Keycloak (đăng nhập, đăng ký, đổi/quên mật khẩu) thay vì gọi auth-service.
 */
import { signIn, signOut } from 'next-auth/react';
import { ChangePasswordForm, ResetPasswordForm } from '@/models/form';

const callbackUrl = () => (typeof window !== 'undefined' ? window.location.href : '/');

export const login = async (email?: string, _password?: string) =>
  signIn('keycloak', { callbackUrl: callbackUrl() }, email ? { login_hint: email } : undefined);

export const logout = async (_email?: string) => signOut({ callbackUrl: '/' });

/** Mở trang đăng ký của Keycloak (OIDC prompt=create). */
export const signup = async (_data?: unknown) =>
  signIn('keycloak', { callbackUrl: callbackUrl() }, { prompt: 'create' });

/** Keycloak yêu cầu nhập mật khẩu hiện tại rồi đặt mật khẩu mới (Application Initiated Action). */
export const changePassword = async (_data?: ChangePasswordForm) =>
  signIn('keycloak', { callbackUrl: callbackUrl() }, { kc_action: 'UPDATE_PASSWORD' });

/** Quên mật khẩu: Keycloak gửi email đặt lại từ trang đăng nhập. */
export const resetPassword = async (_data?: ResetPasswordForm) => login();
export const generateOTP = async (_data?: unknown) => login();
export const verifyOTP = async (_data?: unknown) => login();
