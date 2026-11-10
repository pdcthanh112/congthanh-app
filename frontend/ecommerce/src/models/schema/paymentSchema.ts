import { z } from 'zod';
import { useTranslations } from 'next-intl';
import { isValidLuhn } from '@/utils';

export const addPaymentCardSchema = (t: ReturnType<typeof useTranslations>) => {
  return z.object({
    cardNumber: z
      .string()
      .regex(/^\d{16}$/, 'Số thẻ phải chứa đúng 16 chữ số')
      .refine(isValidLuhn, 'Số thẻ không hợp lệ'),
    expiredDate: z
      .string()
      .regex(/^(0[1-9]|1[0-2])\/\d{2}$/, 'Ngày hết hạn phải có định dạng MM/YY')
      .refine((date) => {
        const [month, year] = date.split('/').map(Number);
        const currentYear = new Date().getFullYear() % 100; // Lấy 2 số cuối của năm hiện tại
        const currentMonth = new Date().getMonth() + 1;
        return year > currentYear || (year === currentYear && month >= currentMonth);
      }, 'Ngày hết hạn không hợp lệ hoặc đã quá hạn'),
    cvv: z.string().regex(/^\d{3,4}$/, t('auth.validation.field_invalid', { field: t('payment.add_card.cvv') })),
    cardHolder: z
      .string()
      .min(5, 'Tên chủ thẻ phải có ít nhất 5 ký tự')
      .max(50, 'Tên chủ thẻ không được quá 50 ký tự')
      .regex(/^[A-Za-z\s]+$/, 'Tên chủ thẻ chỉ được chứa chữ cái'),
  });
};

export type AddPaymentCardSchemaType = z.infer<Awaited<ReturnType<typeof addPaymentCardSchema>>>;
