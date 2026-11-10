import React from 'react';
import './AppContent.module.scss';

const AppContent = ({ children }: { children: React.ReactNode }) => {
  return <main className="min-h-[calc(100vh-270px)] bg-[#F5F5F5]">{children}</main>;
};

export default React.memo(AppContent);
