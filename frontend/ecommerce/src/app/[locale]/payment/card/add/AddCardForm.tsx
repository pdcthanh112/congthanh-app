'use client';
import React, { useState } from 'react';
import { SubmitHandler, useForm } from 'react-hook-form';
import { Button, Form, FormControl, FormField, FormItem, FormMessage } from '@/components/ui';
import { zodResolver } from '@hookform/resolvers/zod';
import { addPaymentCardSchema, AddPaymentCardSchemaType } from '@/models/schema/paymentSchema';
import Image from 'next/image';
import { Dialog, TextField } from '@mui/material';
import { CircleHelp } from 'lucide-react';
import { useTranslations } from 'next-intl';
import CVVExplain from '@/assets/images/cvv-explain.png';
import { useMutation } from '@tanstack/react-query';
import { addPaymentCard } from '@/api/paymentApi';
// import InputCardNumber from '@/components/Payment/InputCardNumber';

export default function AddCardForm() {
  //   const [cardType, setCardType] = useState('unknown');
  const [openDialog, setOpenDialog] = useState<boolean>(false);
  const t = useTranslations();

  const AddPaymentCardSchema = addPaymentCardSchema(t);

  const {} = useMutation({
    mutationKey: ['payment'],
    mutationFn: async () => await addPaymentCard(),
  });

  const form = useForm<AddPaymentCardSchemaType>({
    defaultValues: {},
    resolver: zodResolver(AddPaymentCardSchema),
  });

  const onSubmit: SubmitHandler<AddPaymentCardSchemaType> = (data) => {
    console.log(data);
  };

  return (
    <React.Fragment>
      <Form {...form}>
        <form onSubmit={form.handleSubmit(onSubmit)} className="mt-4 grid grid-cols-12">
          {/* <FormField
            name="cardNumber"
            control={form.control}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0 col-span-12">
                <FormControl>
                  <InputCardNumber
                    value={field.value}
                    onChange={(value) => form.setValue('cardNumber', value, { shouldValidate: true })}
                    onCardTypeChange={(type) => setCardType(type)}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          /> */}

          <FormField
            name="cardNumber"
            control={form.control}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0 col-span-12">
                <FormControl>
                  <TextField
                    label="Card number"
                    {...field}
                    className="w-full"
                    error={!!form.formState.errors.cardNumber}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            name="expiredDate"
            control={form.control}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0 col-span-8 mr-4">
                <FormControl>
                  <TextField
                    label="Expired date"
                    {...field}
                    className="w-full"
                    error={!!form.formState.errors.expiredDate}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            name="cvv"
            control={form.control}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0 col-span-4">
                <FormControl>
                  <div className="flex relative items-center">
                    <TextField label="CVV" {...field} className="w-full" error={!!form.formState.errors.cvv} />
                    <CircleHelp className="absolute right-3 hover:cursor-pointer" onClick={() => setOpenDialog(true)} />
                  </div>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            name="cardHolder"
            control={form.control}
            render={({ field }) => (
              <FormItem className="h-24 space-y-0 col-span-12">
                <FormControl>
                  <TextField
                    label="Card Holder Name"
                    {...field}
                    className="w-full"
                    error={!!form.formState.errors.cardHolder}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <div className="flex justify-end gap-2 col-span-12">
            <Button className="bg-white border-2 border-green-500 text-green-500 hover:bg-green-50">Back</Button>
            <Button disabled={!form.formState.isValid}>Submit</Button>
          </div>
        </form>
      </Form>
      <Dialog open={openDialog} onClose={() => setOpenDialog(false)}>
        <div className="px-8 py-8">
          <div className="font-medium text-lg">CVV</div>
          <Image src={CVVExplain} alt="" width={300} className="mx-auto my-5" />
          <div>CVV là mã xác thực gồm 3 chữ số được in trên mặt sau thẻ tín dụng.</div>
          <div className="flex justify-end mt-3">
            <Button onClick={() => setOpenDialog(false)} className="px-10 py-5">
              OK
            </Button>
          </div>
        </div>
      </Dialog>
    </React.Fragment>
  );
}
