import { Product } from '@/models/types';

export type Order = {
  id: number;
  customer: string;
  total: number;
  note?: string;
  orderDate: number;
  status: string;
};

export type OrderDetail = {
  id: number;
  customer: string;
  total: number;
  note?: string;
  orderDate: number;
  status: string;
  orderCode: string;
  orderItems: OrderItem[];
  shippingAddress: string;
  statusTracking: OrderStatusTracking[];
};

export type OrderItem = {
  id: number;
  quantity: number;
  product: Product;
  status: string;
};

export type OrderStatusTracking = {
  id: number;
  status: string;
  description: string;
  changedAt: Date;
};
