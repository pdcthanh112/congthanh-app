import * as actionName from './name/auth';
import {
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
} from './payload/auth';
import {
  AuthClean,
  EditProfileFailed,
  EditProfileRequested,
  EditProfileStart,
  EditProfileSucceeded,
  LoginFailed,
  LoginRequested,
  LoginStart,
  LoginSucceeded,
  LogoutFailed,
  LogoutRequested,
  LogoutStart,
  LogoutSucceeded,
  SignupFailed,
  SignupRequested,
  SignupStart,
  SignupSucceeded,
} from './type/auth';

export const loginRequested = (payload: LoginRequestedPayload): LoginRequested => ({
  type: actionName.LOGIN_REQUESTED,
  payload,
});

export const loginStart = (payload: LoginStartPayload): LoginStart => ({
  type: actionName.LOGIN_START,
  payload,
});

export const loginSuccess = (payload: LoginSucceededPayload): LoginSucceeded => ({
  type: actionName.LOGIN_SUCCEEDED,
  payload,
});

export const loginFailed = (payload: LoginFailedPayload): LoginFailed => ({
  type: actionName.LOGIN_FAILED,
  payload,
});

export const logoutRequested = (payload: LogoutRequestedPayload): LogoutRequested => ({
  type: actionName.LOGOUT_REQUESTED,
  payload,
});

export const logoutStart = (payload: LogoutStartPayload): LogoutStart => ({
  type: actionName.LOGOUT_START,
  payload,
});

export const logoutSuccess = (payload: LogoutSucceededPayload): LogoutSucceeded => ({
  type: actionName.LOGOUT_SUCCEEDED,
  payload,
});

export const logoutFailed = (payload: LogoutFailedPayload): LogoutFailed => ({
  type: actionName.LOGOUT_FAILED,
  payload,
});

export const signupRequested = (payload: SignupRequestedPayload): SignupRequested => ({
  type: actionName.SIGNUP_REQUESTED,
  payload,
});

export const signupStart = (payload: SignupStartPayload): SignupStart => ({
  type: actionName.SIGNUP_START,
  payload,
});

export const signupSuccess = (payload: SignupSucceededPayload): SignupSucceeded => ({
  type: actionName.SIGNUP_SUCCEEDED,
  payload,
});

export const signupFailed = (payload: SignupFailedPayload): SignupFailed => ({
  type: actionName.SIGNUP_FAILED,
  payload,
});

export const editProfileRequested = (payload: EditProfileRequestedPayload): EditProfileRequested => ({
  type: actionName.EDIT_PROFILE_REQUESTED,
  payload,
});

export const editProfileStart = (payload: EditProfileStartPayload): EditProfileStart => ({
  type: actionName.EDIT_PROFILE_START,
  payload,
});

export const editProfileSuccess = (payload: EditProfileSucceededPayload): EditProfileSucceeded => ({
  type: actionName.EDIT_PROFILE_SUCCEEDED,
  payload,
});

export const editProfileFailed = (payload: EditProfileFailedPayload): EditProfileFailed => ({
  type: actionName.EDIT_PROFILE_FAILED,
  payload,
});

export const authClean = () => ({
  type: actionName.AUTH_CLEAN,
  payload: null,
});
