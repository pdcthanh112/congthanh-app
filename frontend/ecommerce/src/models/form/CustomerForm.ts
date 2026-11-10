export type EditProfileForm = {
  name: string;
  phone: string;
  address: string;
  dob: Date;
  gender: string;
  image: string;
  country: string
};

export type ChangePasswordForm = {
  accountId: string;
  currentPassword: string;
  newPassword: string;
  confirmPassword?: string;
};

export type ResetPasswordForm = {
  accountId: string;
  newPassword: string;
  confirmPassword?: string;
};
