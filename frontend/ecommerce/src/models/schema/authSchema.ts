import { z } from 'zod';
import { useTranslations } from 'next-intl';
import {
  eChangePasswordFormKey,
  eForgetPasswordFormKey,
  eLoginFormKey,
  eResetPasswordFormKey,
} from '@/utils/constants/formKey';
import { PASSWORD_REGEX } from '@/utils/regex';

export const createLoginSchema = (t: ReturnType<typeof useTranslations>) => {
  return z
    .object({
      [eLoginFormKey.Email]: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.email') }))
        .email(t('auth.validation.field_invalid', { field: t('auth.email') })),
      [eLoginFormKey.Password]: z
        .string()
        .trim()
        .min(1, t('auth.validation.field_required', { field: t('auth.password') })),
      remember: z.boolean(),
      fcmToken: z.string().optional(),
      provider: z.string(),
    })
    .strict();
};

// export type LoginSchemaType = z.TypeOf<typeof LoginSchema>;
export type LoginSchemaType = z.infer<Awaited<ReturnType<typeof createLoginSchema>>>;

export const createRegisterSchema = (t: ReturnType<typeof useTranslations>) => {
  return z
    .object({
      email: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.email') }))
        .email(t('auth.validation.field_invalid', { field: t('auth.email') })),
      name: z
        .string()
        .trim()
        .min(2, t('auth.validation.field_required', { field: t('auth.name') }))
        .max(256),
      phone: z
        .string()
        .length(10, t('auth.validation.field_required', { field: t('auth.phone') }))
        .regex(
          /^(\+?\d{1,3}[-.\s]?)?(\(?\d{1,4}\)?[-.\s]?)?[\d\s]{7,15}$/,
          t('auth.validation.field_invalid', { field: t('auth.phone') })
        ),
      password: z
        .string()
        .nonempty(t('auth.validation.field_required', { field: t('auth.password') }))
        .min(8, 'Password at least 8 characters')
        .max(32)
        .regex(new RegExp(PASSWORD_REGEX)),
      confirm: z.string().min(1, t('auth.validation.field_required', { field: t('auth.confirm') })),
    })
    .strict()
    .superRefine(({ confirm, password }, ctx) => {
      if (confirm !== password) {
        ctx.addIssue({
          code: 'custom',
          message: t('auth.validation.not_match'),
          path: ['confirm'],
        });
      }
    });
};

export type RegisterSchemaType = z.infer<Awaited<ReturnType<typeof createRegisterSchema>>>;

export const createForgetPasswordSchema = (t: ReturnType<typeof useTranslations>) => {
  return z
    .object({
      [eForgetPasswordFormKey.Email]: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.email') }))
        .email(),
    })
    .strict();
};

export type ForgetPasswordSchemaType = z.infer<Awaited<ReturnType<typeof createForgetPasswordSchema>>>;

export const resetPasswordSchema = z
  .object({
    [eResetPasswordFormKey.Password]: z.string().min(8, 'Password at least 8 characters'),
    [eResetPasswordFormKey.Confirm]: z.string(),
  })
  .strict()
  .superRefine(({ confirm, password }, ctx) => {
    if (confirm !== password) {
      ctx.addIssue({
        code: 'custom',
        message: 'Confirm does not match',
        path: ['confirm'],
      });
    }
  });

export type resetPasswordType = z.TypeOf<typeof resetPasswordSchema>;

export const createChangePasswordSchema = (t: ReturnType<typeof useTranslations>) => {
  return z
    .object({
      [eChangePasswordFormKey.CurrentPassword]: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.change_password.current_password') })),
      [eChangePasswordFormKey.NewPassword]: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.change_password.new_password') }))
        .min(8, 'Password at least 8 characters')
        .max(32, 'Password maximum 32 characters')
        .regex(new RegExp(PASSWORD_REGEX)),
      [eChangePasswordFormKey.ConfirmPassword]: z
        .string()
        .trim()
        .nonempty(t('auth.validation.field_required', { field: t('auth.change_password.confirm_password') })),
    })
    .strict()
    .superRefine(({ confirmPassword, newPassword }, ctx) => {
      if (confirmPassword !== newPassword) {
        ctx.addIssue({
          code: 'custom',
          message: 'Confirm does not match',
          path: ['confirm'],
        });
      }
    });
};

export type ChangePasswordSchemaType = z.infer<Awaited<ReturnType<typeof createChangePasswordSchema>>>;
