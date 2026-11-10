import * as actionName from '../name/category';
import { FetchCategoryFailedPayload, FetchCategoryRequestedPayload, FetchCategoryStartPayload, FetchCategorySucceededPayload } from '../payload/category';

export interface CategoryState {
  status: 'idle' | 'pending' | 'succeeded' | 'failed'
  error: string | null;
  data: Category[];
}

export type FetchCategoryRequested = {
  type: typeof actionName.FETCH_CATEGORY_REQUESTED;
  payload: FetchCategoryRequestedPayload;
};

export type FetchCategoryStart = {
  type: typeof actionName.FETCH_CATEGORY_START;
  payload: FetchCategoryStartPayload;
};

export type FetchCategorySucceeded = {
  type: typeof actionName.FETCH_CATEGORY_SUCCEEDED;
  payload: FetchCategorySucceededPayload;
};

export type FetchCategoryFailed = {
  type: typeof actionName.FETCH_CATEGORY_FAILED;
  payload: FetchCategoryFailedPayload;
};
