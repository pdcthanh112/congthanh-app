import { Category } from "@models/type";

export interface FetchCategoryRequestedPayload {
  params: any;
}

export interface FetchCategoryStartPayload {
  params: { token: string };
}

export interface FetchCategorySucceededPayload {
  data: Category[];
}

export interface FetchCategoryFailedPayload {
  error: string;
}
