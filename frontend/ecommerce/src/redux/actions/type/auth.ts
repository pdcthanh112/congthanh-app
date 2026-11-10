import * as actionName from '../name/auth';
import {
  AuthCleanPayload,
  EditProfileFailedPayload,
  EditProfileRequestedPayload,
  EditProfileStartPayload,
  EditProfileSucceededPayload,
  LoginFailedPayload,
  LoginRequestedPayload,
  LoginStartPayload,
  LoginSucceededPayload,
  LogoutFailedPayload,
  LogoutRequestedPayload,
  LogoutStartPayload,
  LogoutSucceededPayload,
  SignupFailedPayload,
  SignupRequestedPayload,
  SignupStartPayload,
  SignupSucceededPayload,
} from '../payload/auth';

export interface AuthState {
    status: 'idle' | 'pending' | 'succeeded' | 'failed'
    error: string | null;
    currentUser: any;
  };
  
export type LoginRequested = {
  type: typeof actionName.LOGIN_REQUESTED;
  payload: LoginRequestedPayload;
};

export type LoginStart = {
  type: typeof actionName.LOGIN_START;
  payload: LoginStartPayload;
};

export type LoginSucceeded = {
  type: typeof actionName.LOGIN_SUCCEEDED;
  payload: LoginSucceededPayload;
};

export type LoginFailed = {
  type: typeof actionName.LOGIN_FAILED;
  payload: LoginFailedPayload;
};

export type LogoutRequested = {
  type: typeof actionName.LOGOUT_REQUESTED;
  payload: LogoutRequestedPayload;
};

export type LogoutStart = {
  type: typeof actionName.LOGOUT_START;
  payload: LogoutStartPayload;
};

export type LogoutSucceeded = {
  type: typeof actionName.LOGOUT_SUCCEEDED;
  payload: LogoutSucceededPayload;
};

export type LogoutFailed = {
  type: typeof actionName.LOGOUT_FAILED;
  payload: LogoutFailedPayload;
};

export type SignupRequested = {
  type: typeof actionName.SIGNUP_REQUESTED;
  payload: SignupRequestedPayload;
};

export type SignupStart = {
  type: typeof actionName.SIGNUP_START;
  payload: SignupStartPayload;
};

export type SignupSucceeded = {
  type: typeof actionName.SIGNUP_SUCCEEDED;
  payload: SignupSucceededPayload;
};

export type SignupFailed = {
  type: typeof actionName.SIGNUP_FAILED;
  payload: SignupFailedPayload;
};

export type EditProfileRequested = {
  type: typeof actionName.EDIT_PROFILE_REQUESTED;
  payload: EditProfileRequestedPayload;
};

export type EditProfileStart = {
  type: typeof actionName.EDIT_PROFILE_START;
  payload: EditProfileStartPayload;
};

export type EditProfileSucceeded = {
  type: typeof actionName.EDIT_PROFILE_SUCCEEDED;
  payload: EditProfileSucceededPayload;
};

export type EditProfileFailed = {
  type: typeof actionName.EDIT_PROFILE_FAILED;
  payload: EditProfileFailedPayload;
};

export type AuthClean = {
  type: typeof actionName.AUTH_CLEAN;
  payload: AuthCleanPayload
}
