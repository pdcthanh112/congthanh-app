'use client';
import React from 'react';
import { useForm } from 'react-hook-form';
import { Button, Checkbox } from 'antd';
import { Address } from '@/models/types';
import { Autocomplete, TextField } from '@mui/material';
import countryData from '../../../public/data/country.json';
import { useTranslations } from 'next-intl';
import { UpdateAddressForm } from '@/models/form';
import { Form, FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/components/ui';

type PropsType = {
  address: Address;
  onBack: () => void;
  handleUpdate: (data: UpdateAddressForm) => void;
};

const TabEditAddress = ({ address, onBack, handleUpdate }: PropsType) => {
  const t = useTranslations('common');

  const form = useForm<UpdateAddressForm>();

  return (
    <React.Fragment>
      <h3>Update address</h3>
      <div>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleUpdate)}>
            <input type="hidden" defaultValue={address.id} />
            {/* <input type="hidden" {...register('customer', {})} defaultValue={currentUser.userInfo.accountId} /> */}
            <div className="grid grid-cols-12 gap-4">
              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="h-24 space-y-0">
                    <FormLabel>jlajljlas</FormLabel>
                    <FormControl>
                      <Input
                        type="text"
                        {...field}
                        defaultValue={address?.phone}
                        placeholder="Enter your phone number"
                        className={`focus:outline-hidden ml-3 w-[100%] ${form.formState.errors.phone && 'bg-red-100'}`}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />

              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem className="h-24 space-y-0">
                    <FormLabel>Zip Code/Postal</FormLabel>
                    <FormControl>
                      <Input
                        type="text"
                        {...field}
                        defaultValue={address?.phone}
                        placeholder="Enter your phone number"
                        className={`focus:outline-hidden ml-3 w-[100%] ${form.formState.errors.phone && 'bg-red-100'}`}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormField
              name="country"
              control={form.control}
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Country/Region</FormLabel>
                  <FormControl>
                    <Autocomplete
                      options={countryData}
                      getOptionLabel={(option) => option.name}
                      size={'small'}
                      // defaultValue={address?.country}
                      renderInput={(params) => <TextField {...params} label="" />}
                      onInputChange={(event, value) => {
                        form.setValue('country', value);
                      }}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <div className="grid grid-cols-12 gap-2">
              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>City/State</FormLabel>
                    <FormControl>
                      <Autocomplete
                        options={['Ho Chi Minh']}
                        size={'small'}
                        defaultValue={address?.addressLine1}
                        renderInput={(params) => <TextField {...params} label="" />}
                        onInputChange={(event, value) => {
                          form.setValue('addressLine1', value);
                        }}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />

              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>District</FormLabel>
                    <FormControl>
                      <Autocomplete
                        options={['Ho Chi Minh']}
                        size={'small'}
                        defaultValue={address?.addressLine2}
                        renderInput={(params) => <TextField {...params} label="" />}
                        onInputChange={(event, value) => {
                          form.setValue('addressLine2', value);
                        }}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />

              <FormField
                name="country"
                control={form.control}
                rules={{ required: true }}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Ward</FormLabel>
                    <FormControl>
                      <Autocomplete
                        options={['Ho Chi Minh']}
                        size={'small'}
                        defaultValue={address?.addressLine3}
                        renderInput={(params) => <TextField {...params} label="" />}
                        onInputChange={(event, value) => {
                          form.setValue('addressLine3', value);
                        }}
                      />
                    </FormControl>
                  </FormItem>
                )}
              />
            </div>

            <FormField
              name="country"
              control={form.control}
              rules={{ required: true }}
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Country/Region</FormLabel>
                  <FormControl>
                    <Input
                      type="text"
                      {...form.register('street', {
                        required: 'Address is require',
                      })}
                      defaultValue={address?.street}
                      placeholder="Enter your address"
                      className={`focus:outline-hidden ml-3 w-[100%] ${form.formState.errors.street && 'bg-red-100'}`}
                    />
                  </FormControl>
                </FormItem>
              )}
            />

            <Checkbox>Set as default</Checkbox>

            <div className="flex justify-end">
              <Button type="default" style={{ marginRight: '8px' }} danger onClick={() => onBack()}>
                Cancel
              </Button>
              <Button type="primary" danger htmlType="submit">
                Save
              </Button>
            </div>
          </form>
        </Form>
      </div>
    </React.Fragment>
  );
};

export default TabEditAddress;
