import React from 'react';
import Image from 'next/image';
import LoginImage from '@/assets/images/login-page-image.png';

/** Khung chung cho các trang auth: nội dung bên trái, ảnh minh hoạ bên phải (ẩn trên mobile). */
export default function AuthPageShell({ title, intro, children }: { title: string; intro: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-12 min-h-[32rem]">
      <div className="col-span-12 md:col-span-7 flex items-center">
        <div className="w-full max-w-md mx-auto px-4 py-10">
          <h1 className="mb-3 font-medium text-xl">{title}</h1>
          <p className="mb-6 text-sm text-gray-600">{intro}</p>
          {children}
        </div>
      </div>
      <div className="hidden md:block md:col-span-5 relative">
        <Image src={LoginImage} alt="" fill sizes="40vw" className="object-cover" priority />
      </div>
    </div>
  );
}
