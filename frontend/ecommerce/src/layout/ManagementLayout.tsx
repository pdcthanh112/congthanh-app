import React from 'react';
import ManageNavbar from '@/features/manage/components/manage-navbar';
import type { LayoutProps } from '@/types/layout';

export const ManagementLayout = ({ children }: LayoutProps) => (
  <div className="w-full max-w-6xl mx-auto my-3 px-3 py-2 flex flex-col md:flex-row gap-3">
    <aside className="w-full md:w-1/3 lg:w-1/4">
      <ManageNavbar />
    </aside>
    <div className="bg-white w-full px-3 py-2 rounded">{children}</div>
  </div>
);
