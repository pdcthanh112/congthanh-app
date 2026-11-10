import { FetchCategoryFailedPayload, FetchCategoryStartPayload, FetchCategorySucceededPayload } from '@/redux/actions/payload/category';
import { CategoryState } from '@/redux/actions/type/category';
import { PayloadAction, createAsyncThunk, createSlice } from '@reduxjs/toolkit';
import { getAllCategoryJson } from '@/api/categoryApi';

const initialState: CategoryState = {
  status: 'idle',
  error: null,
  data: [],
};

const categorySlice = createSlice({
  name: 'category',
  initialState: initialState,
  reducers: {
    fetchCategoryStart: (state: CategoryState, action: PayloadAction<FetchCategoryStartPayload>) => {
      state.status = 'pending';
    },
    fetchCategorySucceeded: (state: CategoryState, action: PayloadAction<FetchCategorySucceededPayload>) => {
      state.status = 'succeeded';
      state.data = action.payload.data;
    },
    fetchCategoryFailed: (state: CategoryState, action: PayloadAction<FetchCategoryFailedPayload>) => {
      state.status = 'failed';
      state.error = 'loi';
    }, 
  },
});

export const { fetchCategoryStart, fetchCategorySucceeded, fetchCategoryFailed } = categorySlice.actions;
export default categorySlice.reducer;

export const initialCategory = createAsyncThunk('category/initialCatagory', async () => {
  return await getAllCategoryJson();
})
