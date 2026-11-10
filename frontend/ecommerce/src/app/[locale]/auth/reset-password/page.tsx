import React from 'react';
import ResetPasswordForm from './ResetPasswordForm';
import { Card } from '@mui/material';

export default function ResetPasswordPage() {
  return (
    <div>
      <Card className="w-full sm:w-4/5 md:w-3/5 lg:w-2/5 mx-auto px-4 py-3">
        <h3 className="flex justify-center text-2xl font-semibold mb-[44px]">Reset your password</h3>
        <ResetPasswordForm />
      </Card>
    </div>
  );
}
