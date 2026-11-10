import React, { useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { useTranslations } from 'next-intl';
import { createRegisterSchema, RegisterSchemaType } from '@/models/schema/authSchema';
import { Icon } from '@mui/material';
import { AccountCircle, Email, Password, Visibility, VisibilityOff } from '@mui/icons-material';
import { Input, Button, FormField, Form, FormItem, FormMessage, FormLabel, FormControl } from '@/components/ui';
import { zodResolver } from '@hookform/resolvers/zod';

export default function RegisterComponent() {
  const [showPassword, setShowPassword] = useState<boolean>(false);

  const t = useTranslations();
  const RegisterSchema = createRegisterSchema(t);

  const form = useForm<RegisterSchemaType>({
    defaultValues: {
      email: '',
      name: '',
      password: '',
      phone: '',
    },
    resolver: zodResolver(RegisterSchema),
  });

  const onSubmit: SubmitHandler<RegisterSchemaType> = (data) => console.log(data);

  return (
    <React.Fragment>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)}>
          <FormField
            name="email"
            control={form.control}
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
                      {...field}
                      placeholder={t('placeholder.input_field', { field: t('auth.email') })}
                      className="border-none"
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            name="name"
            control={form.control}
            rules={{ required: true }}
            render={({ field }) => (
              <FormItem className="h-[5.5rem] space-y-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.name')}
                  <span className="text-red-500">*</span>
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border border-gray-500 rounded">
                    <Icon component={AccountCircle} className="ml-2" />
                    <Input
                      {...field}
                      placeholder={t('placeholder.input_field', { field: t('auth.name') })}
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
            control={form.control}
            rules={{ required: true }}
            render={({ field }) => (
              <FormItem className="h-[5.5rem] space-y-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.password')}
                  <span className="text-red-500">*</span>
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border border-gray-500 rounded">
                    <Icon component={Password} className="ml-2" />
                    <Input
                      {...field}
                      type={showPassword ? 'text' : 'password'}
                      placeholder={t('placeholder.input_field', { field: t('auth.password') })}
                      className="border-none"
                    />
                    <Icon
                      component={showPassword ? Visibility : VisibilityOff}
                      fontSize="small"
                      className="mr-3"
                      onClick={() => setShowPassword(!showPassword)}
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            name="confirm"
            control={form.control}
            rules={{ required: true }}
            render={({ field }) => (
              <FormItem className="h-[5.5rem] space-y-0">
                <FormLabel style={{ color: 'inherit' }}>
                  {t('auth.confirm')}
                  <span className="text-red-500">*</span>
                </FormLabel>
                <FormControl>
                  <div className="flex items-center border border-gray-500 rounded">
                    <Icon component={Password} className="ml-2" />
                    <Input
                      {...field}
                      type={showPassword ? 'text' : 'password'}
                      placeholder={t('placeholder.input_field', { field: t('auth.confirm') })}
                      className="border-none"
                    />
                    <Icon
                      component={showPassword ? Visibility : VisibilityOff}
                      fontSize="small"
                      className="mr-3"
                      onClick={() => setShowPassword(!showPassword)}
                    />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <Button className="bg-yellow-400 w-full text-white text-lg font-medium">{t('auth.register')}</Button>
          {/* {isLoadingAuth && <ReactLoading className="ml-2" type="spin" color="#FF4444" width={37} />} */}
        </form>
      </Form>
    </React.Fragment>
  );
}
