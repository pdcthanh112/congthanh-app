import React from 'react';
import { Ban, CheckCheck } from 'lucide-react';
import { Icon } from '@mui/material';
import {
  lengthRegex,
  lowerCharacterRegex,
  numberCharacterRegex,
  specialCharacterRegex,
  upperCharacterRegex,
} from '@/utils/regex';
import { useTranslations } from 'next-intl';

type PropsType = {
  password: string;
};

export default function ValidatePassword({ password }: PropsType) {
  const t = useTranslations('auth.validation.password');

  return (
    <div className="text-black">
      <div className={`${lengthRegex.test(password) && 'text-green-500'}`}>
        <Icon component={lengthRegex.test(password) ? CheckCheck : Ban} fontSize="inherit" />
        <span className="ml-1">{t('length_require', { field: 'Password' })}</span>
      </div>
      <div className={`${lowerCharacterRegex.test(password) && 'text-green-500'}`}>
        <Icon component={lowerCharacterRegex.test(password) ? CheckCheck : Ban} fontSize="inherit" />
        <span className="ml-1">{t('lowercase_letter_require', { field: 'Password' })}</span>
      </div>
      <div className={`${upperCharacterRegex.test(password) && 'text-green-500'}`}>
        <Icon component={upperCharacterRegex.test(password) ? CheckCheck : Ban} fontSize="inherit" />
        <span className="ml-1">{t('uppercase_letter_require', { field: 'Password' })}</span>
      </div>
      <div className={`${numberCharacterRegex.test(password) && 'text-green-500'}`}>
        <Icon component={numberCharacterRegex.test(password) ? CheckCheck : Ban} fontSize="inherit" />
        <span className="ml-1">{t('numberic_character_require', { field: 'Password' })}</span>
      </div>
      <div className={`${specialCharacterRegex.test(password) && 'text-green-500'}`}>
        <Icon component={specialCharacterRegex.test(password) ? CheckCheck : Ban} fontSize="inherit" />
        <span className="ml-1">{t('special_character_require', { field: 'Password' })}</span>
      </div>
    </div>
  );
}
