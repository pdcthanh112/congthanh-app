'use client';

import React, { useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { createRegisterSchema, RegisterSchemaType } from '@/models/schema/authSchema';
import { zodResolver } from '@hookform/resolvers/zod';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, Button, Input } from '@/components/ui';
import { Icon } from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { DatePicker, Divider, Spin, Tooltip } from 'antd';
import { ClientSafeProvider, LiteralUnion, signIn } from 'next-auth/react';
import { AppleIcon, FacebookIcon, GoogleIcon, TwitterIcon } from '@/assets/icons/socialLoginIcon';
import ValidatePassword from '@/features/auth/component/validate-password/ValidatePassword';
import { CheckCheck, LockKeyhole, Mail, Phone, User } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { BuiltInProviderType } from 'next-auth/providers/index';
import DotRequired from '@/components/common/DotRequired';
import { eRegisterFormKey } from '@/utils/constants/formKey';

type PropsType = {
  providers: Record<LiteralUnion<BuiltInProviderType, string>, ClientSafeProvider> | null;
};

export default function RegisterForm({ providers }: PropsType) {
  const [showPassword, setShowPassword] = useState<boolean>(false);
  const [showModalValidatePassword, setShowModalValidatePassword] = useState<boolean>(false);

  const t = useTranslations();

  const RegisterSchema = createRegisterSchema(t);

  const formRegister = useForm<RegisterSchemaType>({
    resolver: zodResolver(RegisterSchema),
    defaultValues: {
      [eRegisterFormKey.Email]: '',
      [eRegisterFormKey.Name]: '',
      [eRegisterFormKey.Password]: '',
      confirm: '',
      [eRegisterFormKey.Phone]: '',
    },
  });

  const onSubmit: SubmitHandler<RegisterSchemaType> = (data) => {
    console.log('asflafjfksf', data);
  };

  type SupportedProviderId = 'google' | 'facebook' | 'twitter' | 'apple';
  function isSupportedProvider(providerId: string): providerId is SupportedProviderId {
    return ['google', 'facebook', 'twitter', 'apple'].includes(providerId);
  }
  const providerIcons: Record<
    SupportedProviderId,
    {
      icon: (props: React.SVGProps<SVGSVGElement>) => React.ReactElement;
      bgColor: string;
    }
  > = {
    google: { icon: GoogleIcon, bgColor: 'bg-red-400' },
    facebook: { icon: FacebookIcon, bgColor: 'bg-blue-500' },
    twitter: { icon: TwitterIcon, bgColor: 'bg-blue-300' },
    apple: { icon: AppleIcon, bgColor: 'bg-gray-300' },
  };

  return (
    <React.Fragment>
      <Form {...formRegister}>
        <form onSubmit={formRegister.handleSubmit(onSubmit)} className="grid grid-cols-12 gap-4">
          <FormField
            control={formRegister.control}
            name={eRegisterFormKey.Email}
            render={({ field }) => (
              <FormItem className="h-24 col-span-12 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.email')}
                  <DotRequired />
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border-2 px-3 rounded">
                    <Mail />
                    <Input
                      placeholder={t('placeholder.input_field', { field: t('auth.email') })}
                      type="text"
                      {...field}
                      className="border-none"
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={formRegister.control}
            name={eRegisterFormKey.Name}
            render={({ field }) => (
              <FormItem className="h-24 col-span-12 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.name')}
                  <DotRequired />
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border-2 px-3 rounded">
                    <User />
                    <Input
                      type="text"
                      placeholder={t('placeholder.input_field', { field: t('auth.name') })}
                      {...field}
                      className="border-none"
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={formRegister.control}
            name={eRegisterFormKey.Password}
            render={({ field }) => (
              <FormItem className="h-24 w-full col-span-12 md:col-span-6 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.password')}
                  <DotRequired />
                </FormLabel>
                <FormControl>
                  <Tooltip
                    title={<ValidatePassword password={formRegister.getValues('password')} />}
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
            control={formRegister.control}
            name="confirm"
            render={({ field }) => (
              <FormItem className="h-24 w-full col-span-12 md:col-span-6 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.confirm')}
                  <DotRequired />
                </FormLabel>
                <FormControl>
                  <div className="flex relative items-center border-2 px-3 rounded">
                    <LockKeyhole />
                    <Input
                      type={showPassword ? 'text' : 'password'}
                      placeholder={t('placeholder.input_field', { field: t('auth.confirm') })}
                      {...field}
                      className="border-none"
                    />
                    {formRegister.getValues('password') === formRegister.watch('confirm') ? (
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

          <FormField
            control={formRegister.control}
            name="password"
            render={({ field }) => (
              <FormItem className="h-24 w-full col-span-12 md:col-span-6 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.phone')}</FormLabel>
                <FormControl>
                  <div className="relative flex items-center border-2 px-3 rounded">
                    <Phone />
                    <Input type="text" className="border-none" {...field} />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={formRegister.control}
            name="confirm"
            render={({ field }) => (
              <FormItem className="h-24 w-full col-span-12 md:col-span-6 space-y-0 gap-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.date_of_birth')}</FormLabel>
                <FormControl>
                  <DatePicker
                    size="large"
                    className="focus:outline-hidden w-full border-2 border-gray-200"
                    {...field}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button type="submit" className="col-span-12 mt-3">
            {t('auth.register')}
          </Button>
        </form>
      </Form>

      <Divider>{t('common.or')}</Divider>

      <div className="grid grid-cols-12 gap-3 mt-3">
        {Object.values(providers!).map((provider, idx) => {
          if (provider.id === 'credentials') return;
          if (isSupportedProvider(provider.id)) {
            return (
              <div
                key={idx}
                className={`${
                  providerIcons[provider.id].bgColor
                } flex px-3 py-3 mb-3 hover:cursor-pointer rounded-lg col-span-12 md:col-span-6`}
                title={t('auth.login_with_social', { social: provider.name })}
                onClick={() =>
                  signIn(provider.id, { callbackUrl: '/home', redirect: false }).catch(() =>
                    console.error('Sign in error:')
                  )
                }
              >
                <Icon component={providerIcons[provider.id].icon} className="h-2" />
                <span className="ml-3 text-white font-medium truncate">
                  {t('auth.login_with_social', { social: provider.name })}
                </span>
              </div>
            );
          }
        })}
      </div>
    </React.Fragment>
  );
}
