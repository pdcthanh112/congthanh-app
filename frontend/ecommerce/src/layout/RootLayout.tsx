'use client';
import React from 'react';
import { LayoutProps } from '@/app/[locale]/page';
import AuthModal from '@/features/auth/component/auth-modal';
import { ToastContainer } from 'react-toastify';
import useAppModalStore from '@/store/useAppModal';

export const RootLayout = ({ children }: LayoutProps) => {
  const openModalAuth = useAppModalStore(state => state);

  return (
    <React.Fragment>
      {children}
      {openModalAuth && <AuthModal />}
      <ToastContainer
        position="top-right"
        autoClose={5000}
        hideProgressBar={false}
        newestOnTop={false}
        closeOnClick
        rtl={false}
        pauseOnFocusLoss
        draggable
        pauseOnHover
        theme="light"
      />
    </React.Fragment>
  );
};
