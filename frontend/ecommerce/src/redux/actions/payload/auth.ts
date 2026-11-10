export type LoginRequestedPayload = {
  email: string;
  password: string;
};
export type LoginStartPayload = {
  params: { email: string; password: string };
};

export type LoginSucceededPayload = {
  userInfo: {};
  tokenData: {};
};

export type LoginFailedPayload = {
  error: string;
};
export type LogoutRequestedPayload = {
  email: string;
};
export type LogoutStartPayload = {
  params: { email: string; password: string };
};

export type LogoutSucceededPayload = {
  data: {};
};

export type LogoutFailedPayload = {
  error: string;
};

export type SignupRequestedPayload = {
  params: { email: string; password: string };
};

export type SignupStartPayload = {
  params: { email: string; password: string };
};

export type SignupSucceededPayload = {
  data: {};
};

export type SignupFailedPayload = {
  error: string;
};

export type EditProfileRequestedPayload = {
  params: { email: string; password: string };
};

export type EditProfileStartPayload = {
  params: { email: string; password: string };
};

export type EditProfileSucceededPayload = {
  data: {};
};

export type EditProfileFailedPayload = {
  error: string;
};

export type AuthCleanPayload = {
  token: string;
};
