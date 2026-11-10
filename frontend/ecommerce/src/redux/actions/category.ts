import * as actionName from './name/category';
import { FetchCategoryFailedPayload, FetchCategoryStartPayload, FetchCategorySucceededPayload } from './payload/category';
import { FetchCategoryFailed, FetchCategoryRequested, FetchCategoryStart, FetchCategorySucceeded } from './type/category';

export const fetchCategoryRequested = () => ({
  type: actionName.FETCH_CATEGORY_REQUESTED,
  payload: null,
});

export const fetchCategoryStart = (payload: FetchCategoryStartPayload): FetchCategoryStart => ({
  type: actionName.FETCH_CATEGORY_START,
  payload,
});

export const fetchCategorySucceeded = (payload: FetchCategorySucceededPayload): FetchCategorySucceeded => ({
  type: actionName.FETCH_CATEGORY_SUCCEEDED,
  payload,
});

export const fetchCategoryFailed = (payload: FetchCategoryFailedPayload): FetchCategoryFailed => ({
  type: actionName.FETCH_CATEGORY_FAILED,
  payload,
});
