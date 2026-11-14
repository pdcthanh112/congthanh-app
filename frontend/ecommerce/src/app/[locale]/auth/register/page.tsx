import { getTranslations, setRequestLocale } from 'next-intl/server';
import { Link } from '@/i18n/routing';
import AuthPageShell from '@/features/auth/component/AuthPageShell';
import KeycloakSignIn from '@/features/auth/component/KeycloakSignIn';
import { PATH } from '@/utils/constants/path';
import type { Locale } from '@/lib/locales';

export default async function RegisterPage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations();

  return (
    <AuthPageShell title={`${t('common.welcome')} CongThanhApp - Ecommerce`} intro={t('auth.register_intro')}>
      <KeycloakSignIn mode="register" callbackUrl={`/${locale}/home`} />
      <p className="mt-6 text-sm">
        {t('auth.you_already_have_an_account')}&nbsp;
        <Link href={PATH.AUTH_PATH_URL.LOGIN} className="text-green-600 hover:underline">
          {t('auth.login')}
        </Link>
      </p>
    </AuthPageShell>
  );
}
