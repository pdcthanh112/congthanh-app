import { Customer } from './CustomerModel';

export type LoginResponse = {
  user: Customer;
  accessToken: string;
  refreshToken: string;
  accessTokenMaxAge: any
  refreshTokenMaxAge: any
};
