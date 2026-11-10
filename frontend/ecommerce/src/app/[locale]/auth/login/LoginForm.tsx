'use client';

import React, { useState } from 'react';
import { zodResolver } from '@hookform/resolvers/zod';
import { SubmitHandler, useForm } from 'react-hook-form';
import { Button } from '@/components/ui/button';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage } from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Divider, Icon } from '@mui/material';
import { Visibility, VisibilityOff } from '@mui/icons-material';
import { createLoginSchema, LoginSchemaType } from '@/models/schema/authSchema';
import Link from 'next/link';
import { ClientSafeProvider, LiteralUnion, signIn } from 'next-auth/react';
import { BuiltInProviderType } from 'next-auth/providers/index';
import { AppleIcon, FacebookIcon, GoogleIcon, TwitterIcon } from '@/assets/icons/socialLoginIcon';
import { useTranslations } from 'next-intl';
import { useSyncUserData } from '@/hooks/useSyncData';
import { PATH } from '@/utils/constants/path';
import { useSearchParams } from 'next/navigation';
import { eLoginFormKey } from '@/utils/constants/formKey';

type PropsType = {
  providers: Record<LiteralUnion<BuiltInProviderType, string>, ClientSafeProvider> | null;
  csrfToken: string;
};

export default function LoginForm({ providers, csrfToken }: PropsType) {
  // const [session, loading] = useSession();
  const searchParams = useSearchParams();

  const [showPassword, setShowPassword] = useState<boolean>(false);

  const t = useTranslations();

  const callbackUrl = searchParams.get('callbackUrl') || PATH.HOME;

  const LoginSchema = createLoginSchema(t);

  const { syncData } = useSyncUserData();

  const formLogin = useForm<LoginSchemaType>({
    defaultValues: {
      email: '',
      password: '',
      fcmToken: '',
    },
    resolver: zodResolver(LoginSchema),
  });

  const onSubmit: SubmitHandler<LoginSchemaType> = (data) => {
    console.log('asflafjfksf', data);
    signIn('credentials', { ...data, callbackUrl: callbackUrl, redirect: false });
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
      <Form {...formLogin}>
        <form onSubmit={formLogin.handleSubmit(onSubmit)}>
          <input name="csrfToken" type="hidden" defaultValue={csrfToken} />
          <FormField
            control={formLogin.control}
            name={eLoginFormKey.Email}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0">
                <FormLabel>Email</FormLabel>
                <FormControl>
                  <Input placeholder="example@email.com" type="text" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={formLogin.control}
            name={eLoginFormKey.Password}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0">
                <FormLabel style={{ color: 'inherit' }}>{t('auth.password')}</FormLabel>
                <FormControl>
                  <div className="relative">
                    <Input placeholder="********" type={showPassword ? 'text' : 'password'} {...field} />
                    <Icon
                      fontSize="small"
                      component={showPassword ? Visibility : VisibilityOff}
                      cursor={'pointer'}
                      className="absolute right-3 top-2"
                      onClick={() => setShowPassword(!showPassword)}
                    />
                    <Link
                      href={PATH.AUTH_PATH_URL.FORGET_PASSWORD}
                      className="absolute right-0 hover:underline"
                      title="Forget password"
                    >
                      {t('auth.forgot_password')}
                    </Link>
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <Button type="submit" className="mt-8! w-full">
            {t('auth.login')}
          </Button>
        </form>
      </Form>

      <Divider sx={{ my: 2 }}>{t('common.or')}</Divider>

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
                  signIn(provider.id, { callbackUrl: '/home', redirect: false })
                    .then(async () => await syncData())
                    .catch(() => console.error('Sign in error:'))
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
