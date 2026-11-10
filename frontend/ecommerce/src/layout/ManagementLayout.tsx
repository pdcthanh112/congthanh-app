'use client';
import ManageNavbar from '@/features/manage/components/manage-navbar';
import React from 'react';

type LayoutProps = {
  children: React.ReactNode;
};

export const ManagementLayout = ({ children }: LayoutProps) => {
  return (
    <div className="w-4/5 mx-auto my-3 px-3 py-2 flex gap-2">
      <div className='w-1/3'><ManageNavbar /></div>
      <div className='bg-white w-full px-3 py-2 rounded'>{children}</div>
    </div>
  );
};
