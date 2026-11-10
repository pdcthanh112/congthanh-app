import { create } from 'zustand';

interface AppModalState {
  isOpenModalAuth: boolean;
  openModalAuth: () => void;
  closeModalAuth: () => void;
  reset: () => void;
}

const useAppModalStore = create<AppModalState>((set) => ({
  isOpenModalAuth: false,
  openModalAuth: () => set(() => ({ isOpenModalAuth: true })),
  closeModalAuth: () => set(() => ({ isOpenModalAuth: false })),
  reset: () => set(() => ({ isOpenModalAuth: false })),
}));

export default useAppModalStore;
