import React from 'react';
import Image from 'next/image';
import DefaultImage from '@/assets/images/default-image.jpg';
import EditProfile from './EditProfile';
import { getServerSession } from 'next-auth';
import { Icon } from '@mui/material';
import { BadgeRounded, EmailRounded, CallRounded } from '@mui/icons-material';

type InformationFieldProps = {
  icon: any;
  title: string;
  children: React.ReactNode;
  className?: string;
};

const InformationField: React.FC<InformationFieldProps> = (element) => {
  return (
    <div className={`mb-3 flex items-center ${element.className}`}>
      <span className="font-medium">
        <Icon component={element.icon} size={20} className="mr-2" />
      </span>
      <span className="">{element.children}</span>
    </div>
  );
};

async function getUserInfo(email: string) {
  return await getUserInfo(email);
}

const userInfo = {
  email: 'pdcthanh112.dev@gmail.com',
  name: 'Cristiano Ronaldo',
  image: 'https://upload.wikimedia.org/wikipedia/commons/e/e7/Leucanthemum_vulgare_%27Filigran%27_Flower_2200px.jpg',
  phone: '0123456789',
  dob: '01/12/2000',
  address: 'Lầu 7, Tòa nhà Central Park, 208 Nguyễn Trãi, Phường Phạm Ngũ Lão, Quận 1, TPHCM',
  gender: 'MALE',
};

export default async function ProfilePage() {
  const session = await getServerSession();
  console.log('SSSSSSSSSSSSSSSSSSSSSS', session);
  const user = session?.user;

  if (!user) return <div>Login</div>;
  // const userInfo = await getUserInfo(user.email as string);

  return (
    <div className="relative">
      <div className="px-3 py-2">
        <div className="border-b-2 border-gray-200 pb-2 flex justify-between">
          <div>
            <h3 className="font-medium text-lg">Manage your profile</h3>
            <span className="opacity-90">Quản lý thông tin hồ sơ để bảo mật tài khoản</span>
          </div>
        </div>
        <div className="w-5/6 mx-auto my-4 flex justify-between">
          <div className="">
            <InformationField icon={EmailRounded} title={'Email'} className="w-80" children={userInfo.email} />
            <InformationField icon={BadgeRounded} title={'Name'} className="w-96" children={userInfo.name} />
            <InformationField icon={CallRounded} title={'Phone'} className="w-56" children={userInfo.phone} />
            <InformationField icon={BadgeRounded} title={'Date of birth'} className="w-48 ml-10">
              <input type="date" defaultValue={userInfo.dob.toString()} disabled />
            </InformationField>
            <InformationField icon={BadgeRounded} title={'Address'} className="max-w-[80%]" children={userInfo.address} />
          </div>

          <div className="flex">
            {/* <InformationField icon={UserRoundCheck} title={''}> */}
            <Image src={userInfo.image || DefaultImage} alt={'User image'} width={150} height={150} />
            {/* </InformationField> */}
          </div>
        </div>
      </div>
      <div className="absolute top-2 right-2">
        <EditProfile userInfo={user} />
      </div>
    </div>
  );
}
