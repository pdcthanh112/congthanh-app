export enum StateStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}

export enum ResponseStatus {
  SUCCESS = 'SUCCESS',
  FAILURE = 'FAILURE',
}

export enum StatusName {
  APPROVED = 'APPROVED',
  PENDING = 'PENDING',
  REJECTED = 'REJECTED',
  CANCELED = 'CANCELED',
}

export enum GENDER {
  MALE,
  FEMALE,
  OTHER,
}

export type CardType = 'visa' | 'mastercard' | 'amex' | 'discover' | 'jcb' | 'unionpay' | 'unknown';

export enum OrderStatus {
  PLACED = 'PLACED',
  PREPARING = 'PREPARING',
  SHIPPING = 'SHIPPING',
  DELIVERED = 'DELIVERED',
  CANCELLED = 'CANCELLED',
  RETURNED = 'RETURNED',
  FAILED = 'FAILED',
  COMPLETED = 'COMPLETED',
}
