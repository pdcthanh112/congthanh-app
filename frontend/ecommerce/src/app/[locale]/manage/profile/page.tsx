import React from 'react';
import Image from 'next/image';
import { BadgeCheck, CalendarDays, Mail, MapPin, Phone, type LucideIcon } from 'lucide-react';
import { getServerSession } from 'next-auth';
import { getTranslations, setRequestLocale } from 'next-intl/server';
import DefaultImage from '@/assets/images/default-image.jpg';
import { redirect } from '@/i18n/routing';
import { authOptions } from '@/lib/auth/options';
import type { Locale } from '@/lib/locales';
import EditProfileButton from './EditProfileButton';

function InformationField({ icon: Icon, label, children }: { icon: LucideIcon; label: string; children: React.ReactNode }) {
  return (
    <div className="mb-3 flex items-start gap-2">
      <Icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden />
      <span className="sr-only">{label}</span>
      <span className="break-words">{children}</span>
    </div>
  );
}

// Dữ liệu mẫu, giữ nguyên cho tới khi có API hồ sơ khách hàng.
const userInfo = {
  email: 'pdcthanh112.dev@gmail.com',
  name: 'Cristiano Ronaldo',
  image: 'https://upload.wikimedia.org/wikipedia/commons/e/e7/Leucanthemum_vulgare_%27Filigran%27_Flower_2200px.jpg',
  phone: '0123456789',
  dob: '01/12/2000',
  address: 'Lầu 7, Tòa nhà Central Park, 208 Nguyễn Trãi, Phường Phạm Ngũ Lão, Quận 1, TPHCM',
  gender: 'MALE',
};

export default async function ProfilePage({ params }: { params: Promise<{ locale: Locale }> }) {
  const { locale } = await params;
  setRequestLocale(locale);
  const t = await getTranslations('manage');

  // Middleware đã chặn khách chưa đăng nhập; kiểm tra lại ở server cho chắc.
  const session = await getServerSession(authOptions);
  if (!session?.user) redirect({ href: { pathname: '/auth/login', query: { callbackUrl: `/${locale}/manage/profile` } }, locale });

  return (
    <section>
      <div className="flex flex-wrap items-start justify-between gap-3 border-b pb-2">
        <div>
          <h1 className="text-lg font-medium">{t('profile_title')}</h1>
          <p className="opacity-90">{t('profile_subtitle')}</p>
        </div>
        <EditProfileButton />
      </div>
      <div className="my-4 flex flex-col-reverse gap-6 md:flex-row md:justify-between">
        <div className="min-w-0">
          <InformationField icon={Mail} label="Email">
            {userInfo.email}
          </InformationField>
          <InformationField icon={BadgeCheck} label={t('name')}>
            {userInfo.name}
          </InformationField>
          <InformationField icon={Phone} label={t('phone')}>
            {userInfo.phone}
          </InformationField>
          <InformationField icon={CalendarDays} label={t('dob')}>
            {userInfo.dob}
          </InformationField>
          <InformationField icon={MapPin} label={t('address')}>
            {userInfo.address}
          </InformationField>
        </div>
        <Image
          src={userInfo.image || DefaultImage}
          alt=""
          width={150}
          height={150}
          className="h-[150px] w-[150px] rounded-full object-cover"
        />
      </div>
    </section>
  );
}
