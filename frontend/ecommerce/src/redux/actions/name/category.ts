import { createAction } from '@reduxjs/toolkit';

export const FETCH_CATEGORY_REQUESTED = createAction('category/fetchCategoryRequested');
export const FETCH_CATEGORY_START = createAction('category/fetchCategoryStart');
export const FETCH_CATEGORY_SUCCEEDED = createAction('category/fetchCategorySucceeded');
export const FETCH_CATEGORY_FAILED = createAction('category/fetchCategoryFailed');
