import { configureStore } from '@reduxjs/toolkit';
import { useDispatch, useSelector, useStore } from 'react-redux';
import createSagaMiddleware from 'redux-saga';
import rootSaga from './sagas';
import { persistStore, persistReducer, FLUSH, PAUSE, PERSIST, PURGE, REGISTER, REHYDRATE } from 'redux-persist';
import storage from 'redux-persist/lib/storage';
import { rootReducer } from './reducers';

const sagaMiddleware = createSagaMiddleware();

const persistConfig = {
  key: 'root',
  storage,
  cart: ['data'] // Chỉ persist data, bỏ qua loading, error...
};

const persistedReducer = persistReducer(persistConfig, rootReducer);

export const store = () => {
  if (typeof window === 'undefined') {
    return configureStore({
      reducer: rootReducer,
    });
  } else {
    const Store = configureStore({
      reducer: persistedReducer,
      middleware: (getDefaultMiddleware) =>
        getDefaultMiddleware({
          serializableCheck: {
            ignoredActions: [FLUSH, REHYDRATE, PAUSE, PERSIST, PURGE, REGISTER],
          },
          thunk: true,
        }).concat([sagaMiddleware]),
      devTools: process.env.NODE_ENV !== 'production',
    });
    Store.__persistor = persistStore(Store);
    sagaMiddleware.run(rootSaga);
    return Store;
  }
};


export type AppStore = ReturnType<typeof store>;
export type RootState = ReturnType<AppStore['getState']>;
type AppDispatch = AppStore['dispatch'];

export const useAppStore = useStore.withTypes<AppStore>();
export const useAppDispatch = useDispatch.withTypes<AppDispatch>();
export const useAppSelector = useSelector.withTypes<RootState>();
