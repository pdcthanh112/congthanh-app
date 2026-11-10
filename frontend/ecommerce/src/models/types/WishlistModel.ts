import { Product } from "@/models/types";

export type Wishlist = {
  id: string;
  custommer: string;
  product: Product[];
}
