export type CreateCartForm = {
  name: string;
  customer: string;
  isDefault: boolean;
};

export type AddToCartForm = {
  productId: string;
  quantity: number;
  cartId: string;
};

export type UpdateCartItemForm = {
  itemId: number;
  quantity: number;
};
