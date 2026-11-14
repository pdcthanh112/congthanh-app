'use client';

import { AppleIcon, FacebookIcon, GoogleIcon } from '@/assets/icons/socialLoginIcon';
import { Button } from '@/components/ui';
import { login, register, type SocialProvider } from '@/lib/auth/actions';
import { useTranslations } from 'next-intl';
import React from 'react';

const SOCIALS: { id: SocialProvider; name: string; icon: (p: React.SVGProps<SVGSVGElement>) => React.ReactElement }[] =
  [
    { id: 'google', name: 'Google', icon: GoogleIcon },
    { id: 'facebook', name: 'Facebook', icon: FacebookIcon },
    { id: 'apple', name: 'Apple', icon: AppleIcon },
  ];

type Props = {
  mode?: 'login' | 'register';
  callbackUrl?: string;
};

export default function KeycloakSignIn({ mode = 'login', callbackUrl }: Props) {
  const t = useTranslations('auth');

  return (
    <div className="flex flex-col gap-3">
      <Button
        type="button"
        className="w-full"
        onClick={() => (mode === 'login' ? login(callbackUrl) : register(callbackUrl))}
      >
        {mode === 'login' ? t('continue_with_account') : t('create_account')}
      </Button>
      <div className="flex items-center gap-3 text-sm text-gray-500" aria-hidden>
        <span className="h-px flex-1 bg-gray-300" />
        {t('or')}
        <span className="h-px flex-1 bg-gray-300" />
      </div>
      {SOCIALS.map(({ id, name, icon: Icon }) => (
        <Button
          key={id}
          type="button"
          variant="outline"
          className="w-full justify-start gap-3"
          onClick={() => login(callbackUrl, id)}
        >
          <Icon className="h-5 w-5" aria-hidden />
          {t('login_with_social', { social: name })}
        </Button>
      ))}
    </div>
  );
}
