import { PayloadAction } from '@reduxjs/toolkit';
import { put, takeEvery } from 'redux-saga/effects';
import * as api from '@/api/categoryApi';
import { fetchCategoryFailed, fetchCategoryStart, fetchCategorySucceeded } from '@/redux/reducers/categoryReducer';
import { FETCH_CATEGORY_REQUESTED } from '@/redux/actions/name/category';


function* fetchCategory(action: PayloadAction<any>) {
  try {
    yield put(fetchCategoryStart(action.payload));
    const { data } = yield api.getAllCategory();
    yield put(fetchCategorySucceeded({data: data}));
  } catch (e) {
    yield put(fetchCategoryFailed(action.payload));
  }
}

export function* categorySaga() {
  yield takeEvery(FETCH_CATEGORY_REQUESTED, fetchCategory);
}
