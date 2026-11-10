'use client';
import React, { useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { createChangePasswordSchema, ChangePasswordSchemaType } from '@/models/schema/authSchema';
import { useTranslations } from 'next-intl';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button, Input } from '@/components/ui';
import Link from 'next/link';
import { Icon } from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { Tooltip } from 'antd';
import ValidatePassword from '@/features/auth/component/validate-password/ValidatePassword';
import { LockKeyhole } from 'lucide-react';
import { PATH } from '@/utils/constants/path';

export default function ChangePasswordForm() {
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [showModalValidatePassword, setShowModalValidatePassword] = useState<boolean>(false);

  const t = useTranslations();

  const ChangePasswordSchema = createChangePasswordSchema(t);

  const form = useForm<ChangePasswordSchemaType>({
    defaultValues: {},
    resolver: zodResolver(ChangePasswordSchema),
  });

  const onSubmit: SubmitHandler<ChangePasswordSchemaType> = (data) => {
    console.log('asflafjfksf', data);
  };
  return (
    <React.Fragment>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormField
            control={form.control}
            name="currentPassword"
            render={({ field }) => (
              <FormItem className="h-24 space-y-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.change_password.current_password')}</FormLabel>
                <FormControl>
                  <div className="relative">
                    <div className="flex items-center border-2 px-3 rounded">
                      <LockKeyhole />
                      <Input
                        placeholder={t('placeholder.input_field', {
                          field: t('auth.change_password.current_password'),
                        })}
                        type="password"
                        {...field}
                        className="border-none"
                      />
                    </div>
                    <Link
                      href={PATH.AUTH_PATH_URL.FORGET_PASSWORD}
                      className="absolute right-0 hover:underline"
                      title={t('auth.forgot_password')}
                    >
                      {t('auth.forgot_password')}
                    </Link>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="newPassword"
            render={({ field }) => (
              <FormItem className="h-24 space-y-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.change_password.new_password')}</FormLabel>
                <FormControl>
                  <Tooltip
                    title={<ValidatePassword password={form.getValues('newPassword')} />}
                    placement="top"
                    color="#fff"
                    overlayStyle={{ maxWidth: '500px' }}
                    fresh
                    open={showModalValidatePassword}
                  >
                    <div className="relative flex items-center border-2 px-3 rounded">
                      <LockKeyhole />
                      <Input
                        type={showPassword ? 'password' : 'text'}
                        {...field}
                        className="border-none"
                        placeholder={t('placeholder.input_field', { field: t('auth.change_password.new_password') })}
                        onFocus={() => setShowModalValidatePassword(true)}
                        onBlur={() => setShowModalValidatePassword(false)}
                      />
                      <Icon
                        fontSize="small"
                        component={showPassword ? VisibilityOff : Visibility}
                        cursor={'pointer'}
                        className="absolute right-3 top-2"
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
            control={form.control}
            name="confirmPassword"
            render={({ field }) => (
              <FormItem className="h-24 space-y-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.change_password.confirm_password')}</FormLabel>
                <FormControl>
                  <div className="relative flex items-center border-2 px-3 rounded">
                    <LockKeyhole />
                    <Input
                      placeholder={t('placeholder.input_field', { field: t('auth.change_password.confirm_password') })}
                      type={showPassword ? 'password' : 'text'}
                      {...field}
                      className="border-none"
                    />
                    <Icon
                      fontSize="small"
                      component={showPassword ? VisibilityOff : Visibility}
                      cursor={'pointer'}
                      className="absolute right-3 top-2"
                      onClick={() => setShowPassword(!showPassword)}
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="mt-8! w-full">
            {t('common.save')}
          </Button>
        </form>
      </Form>
    </React.Fragment>
  );
}
