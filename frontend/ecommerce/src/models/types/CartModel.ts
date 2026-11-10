import { Product } from '@/models/types';

export type Cart = {
  id: number;
  product: string;
  quantity: number;
  name: string;
  customerId: string;
  status: string;
  createdDate: Date;
  checkoutDate: Date;
  cartItems: CartItem[];
};

export type CartItem = {
  id: number;
  product: Product;
  // productVariant: string;
  quantity: number;
  cart?: number;
};

export type CartItemDetail = {
  id: number;
  product: {
    id: string;
    name: string;
    slug: string;
    image: string;
  };
  quantity: number;
};
