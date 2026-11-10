import { put, takeEvery } from 'redux-saga/effects';
import * as authApi from '@/api/customerApi';
import { PayloadAction } from '@reduxjs/toolkit';
import * as actionName from '../actions/name/auth';
import {
  editProfileFailed,
  editProfileStart,
  editProfileSucceeded,
  loginFailed,
  loginStart,
  loginSucceeded,
  logoutFailed,
  logoutStart,
  logoutSucceeded,
  signupFailed,
  signupStart,
  signupSucceeded,
} from '@/redux/reducers/authReducer';

function* login(action: PayloadAction<any>) {
  try {
    yield put(loginStart(action.payload));
    const {
      data: { userInfo, tokenData },
    } = yield authApi.login(action.payload.email, action.payload.password);
    if (typeof window !== 'undefined') {
      yield localStorage.setItem('user', userInfo);
      yield localStorage.setItem('token', tokenData);
    }
    yield put(loginSucceeded({userInfo, tokenData}));
  } catch (e) {
    yield put(loginFailed(action.payload));
  }
}

function* logout(action: PayloadAction<any>) {
  try {
    yield put(logoutStart(action.payload));
    yield console.log('MMMMMMMMMMMMMMMMMMMMMMMM', action.payload);
    yield localStorage.removeItem('user')
    yield localStorage.removeItem('token')
    yield put(logoutSucceeded(action.payload));
  } catch (e) {
    yield put(logoutFailed(action.payload));
  }
}

function* signup(action: PayloadAction<any>) {
  try {
    yield put(signupStart(action.payload));
    yield authApi.signup(action.payload);
    yield put(signupSucceeded(action.payload));
  } catch (e) {
    yield put(signupFailed(action.payload));
  }
}

function* editProfile(action: PayloadAction<any>) {
  try {
    yield put(editProfileStart(action.payload));
    yield put(editProfileSucceeded(action.payload));
  } catch (e) {
    yield put(editProfileFailed(action.payload));
  }
}

export function* authSaga() {
  yield takeEvery(actionName.LOGIN_REQUESTED, login);
  yield takeEvery(actionName.LOGOUT_REQUESTED, logout);
  yield takeEvery(actionName.SIGNUP_REQUESTED, signup);
  yield takeEvery(actionName.EDIT_PROFILE_REQUESTED, editProfile);
}
