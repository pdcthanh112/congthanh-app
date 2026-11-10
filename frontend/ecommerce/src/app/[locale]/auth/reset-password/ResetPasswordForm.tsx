'use client';

import React, { useState } from 'react';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, Button, Input } from '@/components/ui';
import { resetPasswordSchema, resetPasswordType } from '@/models/schema/authSchema';
import { zodResolver } from '@hookform/resolvers/zod';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useTranslations } from 'next-intl';
import { Spin, Tooltip } from 'antd';
import ValidatePassword from '@/features/auth/component/validate-password/ValidatePassword';
import { CheckCheck, LockKeyhole } from 'lucide-react';
import { Icon } from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { eResetPasswordFormKey } from '@/utils/constants/formKey';

export default function ResetPasswordForm() {
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [showModalValidatePassword, setShowModalValidatePassword] = useState<boolean>(false);

  const t = useTranslations();

  const resetPasswordForm = useForm<resetPasswordType>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: {
      [eResetPasswordFormKey.Password]: '',
      [eResetPasswordFormKey.Confirm]: '',
    },
  });

  const onSubmit: SubmitHandler<resetPasswordType> = (data) => {
    console.log('asflafjfksf', data);
  };

  return (
    <Form {...resetPasswordForm}>
      <form onSubmit={resetPasswordForm.handleSubmit(onSubmit)} noValidate>
        <FormField
          control={resetPasswordForm.control}
          name={eResetPasswordFormKey.Password}
          render={({ field }) => (
            <FormItem className="h-24 w-full space-y-0 gap-0">
              <FormLabel style={{ color: 'inherit' }}>{t('auth.password')}</FormLabel>
              <FormControl>
                <Tooltip
                  title={<ValidatePassword password={resetPasswordForm.getValues('password')} />}
                  placement="top"
                  color="#fff"
                  overlayStyle={{ maxWidth: '500px' }}
                  fresh
                  open={showModalValidatePassword}
                >
                  <div className="relative flex items-center border-2 px-3 rounded">
                    <LockKeyhole />
                    <Input
                      type={showPassword ? 'text' : 'password'}
                      placeholder={t('placeholder.input_field', { field: t('auth.password') })}
                      className="border-none"
                      {...field}
                      onFocus={() => setShowModalValidatePassword(true)}
                      onBlur={() => setShowModalValidatePassword(false)}
                    />
                    <Icon
                      fontSize="small"
                      component={showPassword ? Visibility : VisibilityOff}
                      cursor={'pointer'}
                      className="absolute right-3 top-4"
                      onClick={() => setShowPassword(!showPassword)}
                    />
                  </div>
                </Tooltip>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={resetPasswordForm.control}
          name={eResetPasswordFormKey.Confirm}
          render={({ field }) => (
            <FormItem className="h-24 w-full space-y-0 gap-0">
              <FormLabel style={{ color: 'inherit' }}>{t('auth.confirm')}</FormLabel>
              <FormControl>
                <div className="flex relative items-center border-2 px-3 rounded">
                  <LockKeyhole />
                  <Input
                    type={showPassword ? 'text' : 'password'}
                    placeholder={t('placeholder.input_field', { field: t('auth.confirm') })}
                    {...field}
                    className="border-none"
                  />
                  {resetPasswordForm.getValues('password') === resetPasswordForm.watch('confirm') ? (
                    <CheckCheck size={20} className="absolute right-2 top-4 text-green-500" />
                  ) : (
                    <Spin size="small" className="absolute right-2 top-4" />
                  )}
                </div>
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        <Button type="submit" className="mt-8! w-full">
          Send
        </Button>
      </form>
    </Form>
  );
}
