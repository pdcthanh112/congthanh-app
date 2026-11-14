import { getTranslations, setRequestLocale } from 'next-intl/server';
import AuthPageShell from '@/features/auth/component/AuthPageShell';
import KeycloakSignIn from '@/features/auth/component/KeycloakSignIn';
import type { Locale } from '@/lib/locales';

/** Quên mật khẩu do Keycloak xử lý (liên kết "Forgot password" trên trang đăng nhập gửi email đặt lại). */
export default async function ForgetPasswordPage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('auth');

  return (
    <AuthPageShell title={t('forgot_password')} intro={t('forgot_password_intro')}>
      <KeycloakSignIn mode="login" callbackUrl={`/${locale}/home`} />
    </AuthPageShell>
  );
}
