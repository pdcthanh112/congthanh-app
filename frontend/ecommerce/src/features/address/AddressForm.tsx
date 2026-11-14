'use client';

import React from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useTranslations } from 'next-intl';
import { Button, Checkbox, Form, FormControl, FormField, FormItem, FormLabel, FormMessage, Input } from '@/components/ui';
import type { AddressInput, AddressLabel, ShippingAddress } from '@/graphql/types';

const LABELS: AddressLabel[] = ['HOME', 'OFFICE', 'OTHER'];

const schema = (t: (key: string) => string) =>
  z.object({
    fullName: z.string().trim().min(1, t('validation.required')),
    phoneNumber: z
      .string()
      .trim()
      .regex(/^\+?[0-9 ]{8,15}$/, t('validation.phone')),
    addressLine: z.string().trim().min(1, t('validation.required')),
    provinceCode: z.string().trim(),
    districtCode: z.string().trim(),
    wardCode: z.string().trim(),
    postalCode: z.string().trim(),
    countryCode: z
      .string()
      .trim()
      .regex(/^[A-Za-z]{2}$/, t('validation.country')),
    label: z.enum(['HOME', 'OFFICE', 'OTHER']),
    makeDefault: z.boolean(),
  });

type Values = z.infer<ReturnType<typeof schema>>;

type Props = {
  initial?: ShippingAddress;
  submitting?: boolean;
  onSubmit: (input: AddressInput, makeDefault: boolean) => void;
  onCancel: () => void;
};

const blankToNull = (v: string) => (v ? v : null);

/** Form thêm/sửa địa chỉ giao hàng, ánh xạ thẳng sang AddressInput của GraphQL. */
export default function AddressForm({ initial, submitting, onSubmit, onCancel }: Props) {
  const t = useTranslations('address');
  const form = useForm<Values>({
    resolver: zodResolver(schema(t)),
    defaultValues: {
      fullName: initial?.fullName ?? '',
      phoneNumber: initial?.phoneNumber ?? '',
      addressLine: initial?.addressLine ?? '',
      provinceCode: initial?.provinceCode ?? '',
      districtCode: initial?.districtCode ?? '',
      wardCode: initial?.wardCode ?? '',
      postalCode: initial?.postalCode ?? '',
      countryCode: initial?.countryCode ?? 'VN',
      label: initial?.label ?? 'HOME',
      makeDefault: initial?.isDefault ?? false,
    },
  });

  const submit = (v: Values) =>
    onSubmit(
      {
        label: v.label,
        fullName: v.fullName,
        phoneNumber: v.phoneNumber,
        addressLine: v.addressLine,
        provinceCode: blankToNull(v.provinceCode),
        districtCode: blankToNull(v.districtCode),
        wardCode: blankToNull(v.wardCode),
        postalCode: blankToNull(v.postalCode),
        countryCode: v.countryCode.toUpperCase(),
      },
      v.makeDefault
    );

  const text = (name: Exclude<keyof Values, 'label' | 'makeDefault'>, label: string, className = 'sm:col-span-6', inputProps: React.ComponentProps<'input'> = {}) => (
    <FormField
      control={form.control}
      name={name}
      render={({ field }) => (
        <FormItem className={className}>
          <FormLabel>{label}</FormLabel>
          <FormControl>
            <Input {...field} {...inputProps} />
          </FormControl>
          <FormMessage />
        </FormItem>
      )}
    />
  );

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(submit)} className="grid grid-cols-1 sm:grid-cols-12 gap-3" noValidate>
        {text('fullName', t('full_name'), 'sm:col-span-6', { autoComplete: 'name' })}
        {text('phoneNumber', t('phone'), 'sm:col-span-6', { autoComplete: 'tel', inputMode: 'tel' })}
        {text('addressLine', t('address_line'), 'sm:col-span-12', { autoComplete: 'street-address' })}
        {text('wardCode', t('ward'), 'sm:col-span-4')}
        {text('districtCode', t('district'), 'sm:col-span-4')}
        {text('provinceCode', t('province'), 'sm:col-span-4')}
        {text('postalCode', t('postal_code'), 'sm:col-span-6', { autoComplete: 'postal-code' })}
        {text('countryCode', t('country'), 'sm:col-span-6', { autoComplete: 'country', maxLength: 2 })}
        <FormField
          control={form.control}
          name="label"
          render={({ field }) => (
            <FormItem className="sm:col-span-6">
              <FormLabel>{t('label')}</FormLabel>
              <FormControl>
                <select
                  {...field}
                  className="h-9 w-full rounded-md border border-input bg-transparent px-3 text-sm"
                >
                  {LABELS.map((l) => (
                    <option key={l} value={l}>
                      {t(`label_${l.toLowerCase()}`)}
                    </option>
                  ))}
                </select>
              </FormControl>
            </FormItem>
          )}
        />
        <FormField
          control={form.control}
          name="makeDefault"
          render={({ field }) => (
            <FormItem className="sm:col-span-6 flex items-end gap-2 pb-2">
              <FormControl>
                <Checkbox checked={field.value} onCheckedChange={(v) => field.onChange(v === true)} />
              </FormControl>
              <FormLabel className="!mt-0">{t('set_default')}</FormLabel>
            </FormItem>
          )}
        />
        <div className="sm:col-span-12 flex justify-end gap-2">
          <Button type="button" variant="outline" onClick={onCancel}>
            {t('cancel')}
          </Button>
          <Button type="submit" disabled={submitting}>
            {t('confirm')}
          </Button>
        </div>
      </form>
    </Form>
  );
}
