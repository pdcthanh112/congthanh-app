import React from 'react';

const AppContent = ({ children }: { children: React.ReactNode }) => (
  <main className="min-h-[calc(100vh-270px)] bg-[#F5F5F5]">{children}</main>
);

export default AppContent;
