'use client';

import React, { useState } from 'react';
import { Card, Divider } from '@mui/material';
import ForgetPasswordForm from './ForgetPasswordForm';
import InputOtpForm from './InputOtpForm';
import Link from 'next/link';
import { PATH } from '@/utils/constants/path';
import { useTranslations } from 'next-intl';

export default function ForgetPasswordPage() {
  const [isConfirmSuceed, setIsconfirmSuceed] = useState<boolean>(false);

  const t = useTranslations();

  const NavigateButton = ({ title, url }: { title: string; url: string }) => (
    <Link href={url} className="w-full text-center border-2 border-gray-500 rounded-md px-3 py-2">
      {title}
    </Link>
  );

  return (
    <React.Fragment>
      <Card className="w-full sm:w-4/5 md:w-3/5 lg:w-2/5 mx-auto px-4 py-3">

        <h3 className="flex justify-center text-2xl font-semibold mb-[44px]">Reset your password</h3>

        <ForgetPasswordForm />

        <Divider className="pt-4">{t('common.or')}</Divider>

        <div className="space-x-2 flex mt-3">
          <NavigateButton title={t('auth.login')} url={PATH.AUTH_PATH_URL.LOGIN} />
          <NavigateButton title={t('auth.register')} url={PATH.AUTH_PATH_URL.REGISTER} />
        </div>
      </Card>

      <InputOtpForm open={isConfirmSuceed} setOpen={setIsconfirmSuceed} />
    </React.Fragment>
  );
}
