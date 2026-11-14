'use client';

import { useTranslations } from 'next-intl';
import { Button } from '@/components/ui';
import { changePassword } from '@/lib/auth/actions';

/** Đổi mật khẩu bằng Keycloak (kc_action=UPDATE_PASSWORD): Keycloak hỏi mật khẩu hiện tại rồi cho đặt mật khẩu mới. */
export default function ChangePasswordPage() {
  const t = useTranslations('auth');

  return (
    <section>
      <h1 className="font-medium text-lg">{t('change_password.title')}</h1>
      <p className="opacity-90 mt-1 mb-6">{t('change_password_intro')}</p>
      <Button onClick={() => changePassword()}>{t('change_password.title')}</Button>
    </section>
  );
}
