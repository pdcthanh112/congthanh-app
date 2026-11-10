import { create } from 'zustand';
import { Category } from '@/models/types';

const useCategoryStore = create((set) => ({
  categories: [],
  setCategories: (categories: Category[]) => set({ categories }),
}));

export default useCategoryStore;
