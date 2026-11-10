import { z } from 'zod';
import { useTranslations } from 'next-intl';
import { GENDER } from '@/utils/constants/enum';

export const updateProfileSchema = (t: ReturnType<typeof useTranslations>) => {
  return z
    .object({
      name: z
        .string()
        .trim()
        .min(1, t('auth.validation.field_required', { field: t('auth.email') })),
      gender: z.enum(['MALE, FEMALE, OTHER']),
      dob: z.date({ required_error: 'required', invalid_type_error: 'date invalid' }),
      addressLine1: z.string().trim().min(1, 'required'),
      addressLine2: z.string().trim().min(1, 'required'),
      addressLine3: z.string().trim().min(1, 'required'),
    })
    .strict();
};

export type UpdateProfileSchemaType = z.infer<Awaited<ReturnType<typeof updateProfileSchema>>>;

// export const createEditProfileSchema = (t: ReturnType<typeof useTranslations>) => {
//   return z
//     .object({
//       // email: z
//       //   .string()
//       //   .trim()
//       //   .min(1, t('auth.validation.field_required', { field: t('auth.email') }))
//       //   .email(t('auth.validation.field_invalid', { field: t('auth.email') })),
//       name: z
//         .string()
//         .trim()
//         .min(1, t('auth.validation.field_required', { field: t('auth.name') })),
//       phone: z
//         .string()
//         .length(10, t('auth.validation.field_required', { field: t('auth.phone') }))
//         .regex(
//           /^(\+?\d{1,3}[-.\s]?)?(\(?\d{1,4}\)?[-.\s]?)?[\d\s]{7,15}$/,
//           t('auth.validation.field_invalid', { field: t('auth.phone') })
//         ),
//       gender: z.enum(['MALE', 'FEMALE', 'OTHER']),
//     })
//     .strict();
// };

// export type EditProfilechemaType = z.infer<Awaited<ReturnType<typeof createEditProfileSchema>>>;
