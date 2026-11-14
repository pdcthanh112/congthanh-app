'use client';
import React from 'react';
import { ToastContainer } from 'react-toastify';
import AuthModal from '@/features/auth/component/auth-modal';
import useAppModalStore from '@/store/useAppModal';
import type { LayoutProps } from '@/types/layout';

export const RootLayout = ({ children }: LayoutProps) => {
  const isOpenModalAuth = useAppModalStore((state) => state.isOpenModalAuth);

  return (
    <React.Fragment>
      {children}
      {isOpenModalAuth && <AuthModal />}
      <ToastContainer position="top-right" autoClose={5000} newestOnTop={false} closeOnClick pauseOnFocusLoss draggable pauseOnHover theme="light" />
    </React.Fragment>
  );
};
