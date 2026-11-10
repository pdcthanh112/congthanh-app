import React, { useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { Input } from '@/components/ui/input';
import { Icon } from '@mui/material';
import { Email, Password, Visibility, VisibilityOff } from '@mui/icons-material';
import Link from 'next/link';
import { signIn } from 'next-auth/react';
import { useTranslations } from 'next-intl';
import { createLoginSchema, LoginSchemaType } from '@/models/schema/authSchema';
import { PATH } from '@/utils/constants/path';
import { zodResolver } from '@hookform/resolvers/zod';
import { Divider } from 'antd';
import { AppleIcon, FacebookIcon, GoogleIcon, TwitterIcon } from '@/assets/icons/socialLoginIcon';
import { Form, FormField, Button, FormItem, FormLabel, FormMessage, FormControl } from '@/components/ui';

export default function LoginComponent() {
  const [showPassword, setShowPassword] = useState<boolean>(false);

  const t = useTranslations();
  const LoginSchema = createLoginSchema(t);

  const formLogin = useForm<LoginSchemaType>({
    defaultValues: {
      email: '',
      password: '',
    },
    resolver: zodResolver(LoginSchema),
  });

  const onSubmit: SubmitHandler<LoginSchemaType> = (data) => {
    console.log('AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', data);
  };

  const SocialLoginComponent = ({
    id,
    name,
    bgColor,
    icon,
  }: {
    id: 'google' | 'facebook' | 'twitter' | 'apple';
    name: 'Google' | 'Facebook' | 'Twitter' | 'Apple';
    bgColor: string;
    icon: any;
  }) => (
    <div
      className={`${bgColor} flex px-3 py-2 mb-3 hover:cursor-pointer rounded-lg`}
      title={t('auth.login_with_social', {social: name})}
      onClick={() =>
        signIn(id)
          .then(() => console.log(`${id} login initiated`))
          .catch((err) => console.error('Sign in error:', err))
      }
    >
      <Icon component={icon} className="h-2" />
      <span className="ml-3 text-white font-medium">{t('auth.login_with_social', { social: name })}</span>
    </div>
  );

  return (
    <React.Fragment>
      <Form {...formLogin}>
        <form onSubmit={formLogin.handleSubmit(onSubmit)}>
          <FormField
            name="email"
            control={formLogin.control}
            rules={{ required: true }}
            render={({ field }) => (
              <FormItem className="h-[5.5rem] space-y-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.email')}
                  <span className="text-red-500">*</span>
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border border-gray-500 rounded">
                    <Icon component={Email} className="ml-2" />
                    <Input
                      type="email"
                      placeholder={t('placeholder.input_field', { field: t('auth.email') })}
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
            name="password"
            control={formLogin.control}
            rules={{ required: true }}
            render={({ field }) => (
              <FormItem className="h-[5.5rem] space-y-0 relative">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.password')}
                  <span className="text-red-500">*</span>
                </FormLabel>
                <FormControl>
                  <div className="relative">
                    <div className="flex items-center border border-gray-500 rounded">
                      <Icon component={Password} className="ml-2" />
                      <Input
                        type={showPassword ? 'text' : 'password'}
                        placeholder={t('placeholder.input_field', { field: t('auth.password') })}
                        {...field}
                        className="border-none"
                      />
                      <Icon
                        component={showPassword ? Visibility : VisibilityOff}
                        fontSize="small"
                        className="mr-3"
                        onClick={() => setShowPassword(!showPassword)}
                      />
                    </div>
                    <Link
                      href={PATH.AUTH_PATH_URL.FORGET_PASSWORD}
                      className="hover:underline text-sm absolute right-0 top-16"
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

          {/* {loginError && <div className="input-error p-2 rounded">Tên đăng nhập hoặc mật khẩu không chính xác</div>} */}

          <div className="mt-3">
            <Button className="bg-yellow-400 w-full text-white text-lg font-medium" type="submit">
              {t('auth.login')}
            </Button>
            {/* {isLoadingAuth && <ReactLoading className="ml-2" type="spin" color="#FF4444" width={37} />} */}
          </div>
        </form>
      </Form>

      <Divider>or</Divider>

      <div className="grid grid-cols-12 gap-3 mt-3">
        <div className="col-span-6">
          <SocialLoginComponent id={'google'} name={'Google'} bgColor={'bg-red-400'} icon={GoogleIcon} />
          <SocialLoginComponent id={'twitter'} name={'Twitter'} bgColor={'bg-blue-300'} icon={TwitterIcon} />
        </div>
        <div className="col-span-6">
          <SocialLoginComponent id={'facebook'} name={'Facebook'} bgColor={'bg-blue-400'} icon={FacebookIcon} />
          <SocialLoginComponent id={'apple'} name={'Apple'} bgColor={'bg-gray-400'} icon={AppleIcon} />
        </div>
      </div>
    </React.Fragment>
  );
}
