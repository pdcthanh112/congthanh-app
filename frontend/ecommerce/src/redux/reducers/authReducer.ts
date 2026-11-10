import { AuthState } from '@/redux/actions/type/auth';
import { PayloadAction, createSlice } from '@reduxjs/toolkit';
import {
  EditProfileFailedPayload,
  EditProfileStartPayload,
  EditProfileSucceededPayload,
  LoginFailedPayload,
  LoginStartPayload,
  LoginSucceededPayload,
  LogoutFailedPayload,
  LogoutStartPayload,
  LogoutSucceededPayload,
  SignupFailedPayload,
  SignupStartPayload,
  SignupSucceededPayload,
} from '@/redux/actions/payload/auth';

const initialState: AuthState = {
  status: 'idle',
  error: null,
  currentUser: null,
};

const authSlice = createSlice({
  name: 'auth',
  initialState: initialState,
  reducers: {
    loginStart: (state: AuthState, action: PayloadAction<LoginStartPayload>) => {
      state.status = 'pending';
    },
    loginSucceeded: (state: AuthState, action: PayloadAction<LoginSucceededPayload>) => {
      state.status = 'succeeded';
      state.error = null;
      state.currentUser = action.payload;
    },
    loginFailed: (state: AuthState, action: PayloadAction<LoginFailedPayload>) => {
      state.status = 'failed';
      state.error = 'loi';
    },
    logoutStart: (state: AuthState, action: PayloadAction<LogoutStartPayload>) => {
      state.status = 'pending';
    },
    logoutSucceeded: (state: AuthState, action: PayloadAction<LogoutSucceededPayload>) => {
      state.status = 'succeeded';
      state.currentUser = null;
    },
    logoutFailed: (state: AuthState, action: PayloadAction<LogoutFailedPayload>) => {
      state.status = 'failed';
      state.error = 'loi';
    },
    signupStart: (state: AuthState, action: PayloadAction<SignupStartPayload>) => {
      state.status = 'pending';
    },
    signupSucceeded: (state: AuthState, action: PayloadAction<SignupSucceededPayload>) => {
      state.status = 'succeeded';
    },
    signupFailed: (state: AuthState, action: PayloadAction<SignupFailedPayload>) => {
      state.status = 'failed';
      state.error = 'loi';
    },
    editProfileStart: (state: AuthState, action: PayloadAction<EditProfileStartPayload>) => {
      state.status = 'pending';
    },
    editProfileSucceeded: (state: AuthState, action: PayloadAction<EditProfileSucceededPayload>) => {
      state.status = 'succeeded';
      state.currentUser = action.payload;
    },
    editProfileFailed: (state: AuthState, action: PayloadAction<EditProfileFailedPayload>) => {
      state.status = 'failed';
      state.error = 'loi';
    },
    authClean: (state: AuthState) => {
      state.status = 'idle'
      state.error = null
    }
  },
});

export const {
  loginStart,
  loginSucceeded,
  loginFailed,
  logoutStart,
  logoutSucceeded,
  logoutFailed,
  signupStart,
  signupSucceeded,
  signupFailed,
  editProfileStart,
  editProfileSucceeded,
  editProfileFailed,
  authClean
} = authSlice.actions;
export default authSlice.reducer;
