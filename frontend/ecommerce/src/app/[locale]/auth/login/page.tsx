import { getTranslations, setRequestLocale } from 'next-intl/server';
import { Link } from '@/i18n/routing';
import AuthPageShell from '@/features/auth/component/AuthPageShell';
import KeycloakSignIn from '@/features/auth/component/KeycloakSignIn';
import { safeCallbackUrl } from '@/lib/auth/actions';
import { PATH } from '@/utils/constants/path';
import type { Locale } from '@/lib/locales';

type Props = {
  params: Promise<{ locale: Locale }>;
  searchParams: Promise<{ callbackUrl?: string }>;
};

export default async function LoginPage({ params, searchParams }: Props) {
  const { locale } = await params;
  setRequestLocale(locale);
  const { callbackUrl } = await searchParams;
  const t = await getTranslations();

  return (
    <AuthPageShell title={`${t('common.welcome')} CongThanhApp - Ecommerce`} intro={t('auth.login_intro')}>
      <KeycloakSignIn mode="login" callbackUrl={safeCallbackUrl(callbackUrl, `/${locale}/home`)} />
      <p className="mt-6 text-sm">
        {t('auth.you_dont_have_an_account')}&nbsp;
        <Link href={PATH.AUTH_PATH_URL.REGISTER} className="text-green-600 hover:underline">
          {t('auth.register')}
        </Link>
      </p>
    </AuthPageShell>
  );
}
