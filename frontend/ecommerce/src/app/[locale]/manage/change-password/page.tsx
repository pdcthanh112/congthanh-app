// 'use client'
import React, { useState } from 'react';
import { useForm, SubmitHandler } from 'react-hook-form';
import { ManagementLayout } from '@/layout';
import { Icon } from '@mui/material';
import { DoneAll, Lock, Password, Visibility, VisibilityOff } from '@mui/icons-material';
import { useTranslations } from 'next-intl';
import ValidatePassword from '@/features/auth/component/validate-password/ValidatePassword';
import { Spin, Tooltip } from 'antd';
import { changePassword } from '@/api/customerApi';
import ChangePasswordForm from './ChangePasswordForm';

export default function ChangePassword() {
  // const [showCurrentPassword, setShowCurrentPassword] = useState<boolean>(false);
  // const [showNewPassword, setShowNewPassword] = useState<boolean>(false);
  // const [showModalNewPassword, setShowModalNewPassword] = useState<boolean>(false);
  // const [showModalConfirmPassword, setShowModalConfirmPassword] = useState<boolean>(false);

  // const [newPassword, setNewPassword] = useState<string>('');
  // const [confirmPassword, setConfirmPassword] = useState<string>('');

  // const t = useTranslations('common');

  // const { register, handleSubmit, formState, setError } = useForm<ChangePasswordForm>();
  // const onSubmit: SubmitHandler<ChangePasswordForm> = (data) => {
  //   console.log('aaaaaaaaaaaa', data);
  //   if (data.newPassword === data.confirmPassword) {
  //     // changePassword(data);
  //   } else {
  //     setError('confirmPassword', { type: 'value', message: 'confirm password is not match' });
  //   }
  // };

  // const PasswordMatchingStatus = () => <div>{newPassword === confirmPassword ? <Icon component={DoneAll} color="success" /> : <Spin />}</div>;

  return (
    <React.Fragment>
      <h3 className="ml-5 font-medium text-lg">Change password</h3>
      <span className="opacity-90">Quản lý thông tin hồ sơ để bảo mật tài khoản</span>
      <div className="w-3/5 mt-3 mx-auto">
        <ChangePasswordForm />
      </div>
      {/* <div className="flex justify-center items-center content-center">
        <form onSubmit={handleSubmit(onSubmit)}>
          <InputField className={formState.errors.currentPassword && 'bg-red-100'}>
            <Icon component={Lock} />
            <input
              type={showCurrentPassword ? 'text' : 'password'}
              placeholder="Enter your current password"
              {...register('currentPassword', { required: 'Please your current password' })}
              className={`focus:outline-hidden ml-3 w-[22rem] ${formState.errors.currentPassword && 'bg-red-100'}`}
            />
            <Icon
              component={showNewPassword ? Visibility : VisibilityOff}
              titleAccess={showCurrentPassword ? t('common.hide_password') : t('common.show_password')}
              fontSize="small"
              onClick={() => setShowCurrentPassword(!showCurrentPassword)}
              className="absolute right-3 mt-1 hover:cursor-pointer"
            />
          </InputField>
          <InputField className={formState.errors.newPassword && 'bg-red-100'}>
            <Tooltip title={<ValidatePassword password={newPassword} />} placement="top" open={showModalNewPassword}>
              <Icon component={Password} />
              <input
                type={showNewPassword ? 'text' : 'password'}
                placeholder="Enter your new password"
                {...register('newPassword', { required: 'Please your new password' })}
                className={`focus:outline-hidden ml-3 w-[22rem] ${formState.errors.newPassword && 'bg-red-100'}`}
                onChange={(e) => setNewPassword(e.target.value)}
                onFocus={() => setShowModalNewPassword(true)}
                onBlur={() => setShowModalNewPassword(false)}
              />
            </Tooltip>
            <Icon
              component={showNewPassword ? Visibility : VisibilityOff}
              titleAccess={showNewPassword ? t('common.hide_password') : t('common.show_password')}
              fontSize="small"
              onClick={() => setShowNewPassword(!showNewPassword)}
              className="absolute right-3 mt-1 hover:cursor-pointer"
            />
          </InputField>
          <InputField className={formState.errors.confirmPassword && 'bg-red-100'}>
            <Tooltip title={<PasswordMatchingStatus />} placement="rightTop" open={confirmPassword !== '' && showModalConfirmPassword}>
              <Icon component={Password} />
              <input
                type={showNewPassword ? 'text' : 'password'}
                placeholder="Enter confirm your password"
                {...register('confirmPassword', { required: 'Please your current password' })}
                className={`focus:outline-hidden ml-3 w-[22rem] ${formState.errors.confirmPassword && 'bg-red-100'}`}
                onChange={(e) => setConfirmPassword(e.target.value)}
                onFocus={() => setShowModalConfirmPassword(true)}
                onBlur={() => setShowModalConfirmPassword(false)}
              />
            </Tooltip>
            <Icon
              component={showNewPassword ? Visibility : VisibilityOff}
              titleAccess={showNewPassword ? t('common.hide_password') : t('common.show_password')}
              fontSize="small"
              onClick={() => setShowNewPassword(!showNewPassword)}
              className="absolute right-3 mt-1 hover:cursor-pointer"
            />
          </InputField>

          <div className="flex justify-center mt-10">
            <Button className="bg-yellow-300 rounded-lg w-[40%]">{t('signup.save')}</Button>
          </div>
        </form>
      </div> */}
    </React.Fragment>
  );
}

// ChangePassword.getLayout = function getLayout(page: React.ReactNode) {
//   return <ManagementLayout>{page}</ManagementLayout>;
// };
