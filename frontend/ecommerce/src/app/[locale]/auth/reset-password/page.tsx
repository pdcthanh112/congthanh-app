import { redirect } from '@/i18n/routing';
import type { Locale } from '@/lib/locales';

/** Đặt lại mật khẩu thực hiện qua liên kết trong email Keycloak; trang cũ chuyển về hướng dẫn quên mật khẩu. */
export default async function ResetPasswordPage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  redirect({ href: '/auth/forget-password', locale });
}
