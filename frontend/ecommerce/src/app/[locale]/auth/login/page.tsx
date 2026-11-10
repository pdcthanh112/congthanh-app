import React from 'react';
import LoginForm from './LoginForm';
import Image from 'next/image';
import LoginImage from '@/assets/images/login-page-image.png';
import { getProviders, getCsrfToken } from 'next-auth/react';
import Link from 'next/link';
import { getTranslations } from 'next-intl/server';

export default async function LoginPage() {
  const t = await getTranslations();

  const providers = await getProviders();
  const csrfToken = await getCsrfToken();

  return (
    <div className="grid grid-cols-12">
      <div
        className="col-span-12 sm:col-span-7 overflow-y-scroll h-[40rem]"
        style={{
          scrollbarWidth: 'none',
          msOverflowStyle: 'none',
        }}
      >
        <div className="w-4/5 md:w-3/5 mx-auto">
          <h3 className="mt-14 mb-5 font-medium text-xl">Welcome to CongThanhApp - Ecommerce</h3>
          <div className="flex justify-end">
            {t('auth.you_dont_have_an_account')}&nbsp;
            <Link href={'/auth/register'} className="text-green-400 hover:underline">
              {t('auth.register')}
            </Link>
          </div>
          <LoginForm providers={providers} csrfToken={csrfToken || ''} />
        </div>
      </div>
      <div className="col-span-12 sm:col-span-5 relative h-full">
        <Image src={LoginImage} alt={''} objectFit="fit" fill />
      </div>
    </div>
  );
}
