'use client';
import React, { useEffect, useState } from 'react';
import { DatePicker, Modal, Radio } from 'antd';
import { SubmitHandler, useForm } from 'react-hook-form';
import { EditProfileForm } from '@/models/form';
import Image from 'next/image';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/components/ui';
import { Autocomplete, Icon, TextField } from '@mui/material';
import { Edit, Email } from '@mui/icons-material';
import { useTranslations } from 'next-intl';
import { zodResolver } from '@hookform/resolvers/zod';
import { updateProfileSchema } from '@/models/schema/userSchema';
import axiosInstance from '@/config/axiosConfig';
import { useQuery } from '@tanstack/react-query';

export default function EditProfile({ userInfo }: { userInfo: any }) {
  const [isOpenModalEdit, setIsOpenModalEdit] = useState<boolean>(false);
  const [isChangePhone, setIsChangePhone] = useState(false);

  const t = useTranslations();
  const EditProfileSchema = updateProfileSchema(t);

  const { data: countries } = useQuery<{ id: number; countryName: string }[]>({
    queryKey: ['get-country'],
    queryFn: async () =>
      await axiosInstance
        .post('http://localhost:5002/api/graphql', {
          // const response = await axiosConfig.post('http://localhost:8080/api/ecommerce/catalog/graphql', {
          query: '{ countries { id countryName countryNameOrigin } }',
        })
        .then((response) => response.data.data.countries),
  });

  const form = useForm<EditProfileForm>({
    resolver: zodResolver(EditProfileSchema),
  });
  const onSubmit: SubmitHandler<EditProfileForm> = (data) => {
    // dispatch(login(data)).then((res) => {
    //   if (res.payload.status === 'SUCCESS') {
    //     navigate.push('/home');
    //   }
    // });
    console.log('RRRRRRRRRRRRRRRRRRRR', data);
  };

  return (
    <React.Fragment>
      <Icon
        titleAccess={t('common.edit')}
        component={Edit}
        onClick={() => setIsOpenModalEdit(true)}
        className="opacity-80 hover:opacity-100 hover:cursor-pointer"
      />

      <Modal
        title="Edit profile"
        open={isOpenModalEdit}
        // open={isOpen}
        onOk={() => setIsOpenModalEdit(false)}
        okText="Save"
        onCancel={() => setIsOpenModalEdit(false)}
        width={800}
        height={600}
        style={{
          overflowY: 'scroll',
          scrollbarWidth: 'none',
          msOverflowStyle: 'none',
        }}
      >
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <div className="grid grid-cols-12 gap-2">
              <div className="col-span-8 grid grid-cols-12 gap-x-3">
                <FormField
                  name="name"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-12 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('auth.email')}
                        <span className="text-red-500">*</span>
                      </FormLabel>
                      <FormControl>
                        <div className="flex items-center border border-gray-500 rounded">
                          <Icon component={Email} className="ml-2" />
                          <Input
                            type="email"
                            defaultValue={userInfo.email}
                            disabled
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
                  name="name"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-12 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('auth.email')}
                        <span className="text-red-500">*</span>
                      </FormLabel>
                      <FormControl>
                        <div className="flex items-center border border-gray-500 rounded">
                          <Icon component={Email} className="ml-2" />
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
                  name="phone"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-6 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('auth.phone')}
                        <span className="text-red-500">*</span>
                      </FormLabel>
                      <FormControl>
                        <div className="flex items-center border border-gray-500 rounded">
                          <Icon component={Email} className="ml-2" />
                          <Input
                            type="tel"
                            placeholder={t('placeholder.input_field', { field: t('auth.phone') })}
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
                  name="gender"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-6 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('auth.gender')}
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
                  name="dob"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-6 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('common.dob')}
                        <span className="text-red-500">*</span>
                      </FormLabel>
                      <FormControl>
                        <div className="flex items-center border border-gray-500 rounded">
                          <Icon component={Email} className="ml-2" />
                          <DatePicker
                            size="large"
                            {...field}
                            className="w-full border-none"
                            style={{ boxShadow: 'none' }}
                          />
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  name="country"
                  control={form.control}
                  rules={{ required: true }}
                  render={({ field }) => (
                    <FormItem className="col-span-6 h-24 space-y-0">
                      <FormLabel style={{ color: 'inherit' }}>
                        {t('common.country')}
                        <span className="text-red-500">*</span>
                      </FormLabel>
                      <FormControl>
                        <div className="flex items-center border border-gray-500 rounded">
                          <Icon component={Email} className="ml-2" />
                          <Autocomplete
                            size="small"
                            disablePortal
                            className="rounded-none border-none focus:border-none h-[38px]"
                            options={countries || []}
                            getOptionLabel={(option) => option.countryName}
                            sx={{
                              width: '100%',
                              '& .MuiOutlinedInput-root': {
                                '& fieldset': {
                                  border: 'none',
                                },
                                '&:hover fieldset': {
                                  border: 'none',
                                },
                                '&.Mui-focused fieldset': {
                                  border: 'none',
                                },
                              },
                            }}
                            renderInput={(params) => (
                              <TextField {...params} className="border-none focus:ring-0 shadow-none" {...field} />
                            )}
                          />
                        </div>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <div className="col-span-4 flex justify-center items-center">
                <Image src={userInfo.image} alt={'User image'} width={100} height={100} />
              </div>
            </div>

            <div className="grid grid-cols-12 gap-x-3">
              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="col-span-4 h-24 space-y-0">
                    <FormLabel style={{ color: 'inherit' }}>
                      {t('common.country')}
                      <span className="text-red-500">*</span>
                    </FormLabel>
                    <FormControl>
                      <div className="flex items-center border border-gray-500 rounded">
                        <Icon component={Email} className="ml-2" />
                        <Autocomplete
                          size="small"
                          disablePortal
                          className="rounded-none border-none focus:border-none h-[38px]"
                          options={countries || []}
                          getOptionLabel={(option) => option.countryName}
                          sx={{
                            width: '100%',
                            '& .MuiOutlinedInput-root': {
                              '& fieldset': {
                                border: 'none',
                              },
                              '&:hover fieldset': {
                                border: 'none',
                              },
                              '&.Mui-focused fieldset': {
                                border: 'none',
                              },
                            },
                          }}
                          renderInput={(params) => (
                            <TextField {...params} className="border-none focus:ring-0 shadow-none" {...field} />
                          )}
                        />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="col-span-4 h-24 space-y-0">
                    <FormLabel style={{ color: 'inherit' }}>
                      {t('common.country')}
                      <span className="text-red-500">*</span>
                    </FormLabel>
                    <FormControl>
                      <div className="flex items-center border border-gray-500 rounded">
                        <Icon component={Email} className="ml-2" />
                        <Autocomplete
                          size="small"
                          disablePortal
                          className="rounded-none border-none focus:border-none h-[38px]"
                          options={countries || []}
                          getOptionLabel={(option) => option.countryName}
                          sx={{
                            width: '100%',
                            '& .MuiOutlinedInput-root': {
                              '& fieldset': {
                                border: 'none',
                              },
                              '&:hover fieldset': {
                                border: 'none',
                              },
                              '&.Mui-focused fieldset': {
                                border: 'none',
                              },
                            },
                          }}
                          renderInput={(params) => (
                            <TextField {...params} className="border-none focus:ring-0 shadow-none" {...field} />
                          )}
                        />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="col-span-4 h-24 space-y-0">
                    <FormLabel style={{ color: 'inherit' }}>
                      {t('common.country')}
                      <span className="text-red-500">*</span>
                    </FormLabel>
                    <FormControl>
                      <div className="flex items-center border border-gray-500 rounded">
                        <Icon component={Email} className="ml-2" />
                        <Autocomplete
                          size="small"
                          disablePortal
                          className="rounded-none border-none focus:border-none h-[38px]"
                          options={countries || []}
                          getOptionLabel={(option) => option.countryName}
                          sx={{
                            width: '100%',
                            '& .MuiOutlinedInput-root': {
                              '& fieldset': {
                                border: 'none',
                              },
                              '&:hover fieldset': {
                                border: 'none',
                              },
                              '&.Mui-focused fieldset': {
                                border: 'none',
                              },
                            },
                          }}
                          renderInput={(params) => (
                            <TextField {...params} className="border-none focus:ring-0 shadow-none" {...field} />
                          )}
                        />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                name="address"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="col-span-12 h-24 space-y-0">
                    <FormLabel style={{ color: 'inherit' }}>
                      {t('common.country')}
                      <span className="text-red-500">*</span>
                    </FormLabel>
                    <FormControl>
                      <div className="flex items-center border border-gray-500 rounded">
                        <Icon component={Email} className="ml-2" />
                        <Autocomplete
                          size="small"
                          disablePortal
                          className="rounded-none border-none focus:border-none h-[38px]"
                          options={countries || []}
                          getOptionLabel={(option) => option.countryName}
                          sx={{
                            width: '100%',
                            '& .MuiOutlinedInput-root': {
                              '& fieldset': {
                                border: 'none',
                              },
                              '&:hover fieldset': {
                                border: 'none',
                              },
                              '&.Mui-focused fieldset': {
                                border: 'none',
                              },
                            },
                          }}
                          renderInput={(params) => (
                            <TextField {...params} className="border-none focus:ring-0 shadow-none" {...field} />
                          )}
                        />
                      </div>
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="italic">
              (<span className="text-red-400 font-bold">*</span>) field is required
            </div>
          </form>
        </Form>
      </Modal>
    </React.Fragment>
  );
}
