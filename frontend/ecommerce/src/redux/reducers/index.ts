import { combineReducers } from "redux";

import authReducer from "./authReducer";
import cartReducer from "./cartReducer";
import categoryReducer from "./categoryReducer";

export const rootReducer = combineReducers({
    auth: authReducer,
    cart: cartReducer,
    category: categoryReducer,
  });