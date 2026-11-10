/** Kiểu dữ liệu khớp supergraph storefront (platform/graphql/storefront/supergraph.graphql). */

export type ID = string;
export type DateTime = string;

export type Money = { amount: number; currency: string; formatted: string };
export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER' | 'DISCONTINUED';
export type RatingSummary = { average: number; count: number; stars: number[] };

export type CategoryRef = { id: ID; name: string; slug: string };
export type Category = CategoryRef & {
  description: string | null;
  image: string | null;
  parentId: ID | null;
  sortOrder: number;
  children: CategoryRef[];
};

export type ProductCard = {
  id: ID;
  name: string;
  slug: string | null;
  thumbnail: string | null;
  price: Money;
  originalPrice: Money;
  discountPercent: number;
  ratingSummary: Pick<RatingSummary, 'average' | 'count'>;
  inWishlist: boolean;
};

export type Product = ProductCard & {
  sku: string;
  description: string | null;
  images: string[];
  brandName: string | null;
  sellerId: ID;
  active: boolean;
  stock: { available: number; status: StockStatus } | null;
  categories: CategoryRef[];
  tags: { id: number; name: string }[];
  ratingSummary: RatingSummary;
};

export type ProductPage = { items: ProductCard[]; page: number; limit: number; totalItems: number; totalPages: number };

export type CartLine = {
  id: ID;
  quantity: number;
  purchasable: boolean;
  issue: string | null;
  unitPrice: Money | null;
  lineTotal: Money | null;
  product: Pick<ProductCard, 'id' | 'name' | 'slug' | 'thumbnail'> | null;
};
export type Cart = { id: ID | null; itemCount: number; checkoutReady: boolean; subtotal: Money; lines: CartLine[] };

export type AddressLabel = 'HOME' | 'OFFICE' | 'OTHER';
export type ShippingAddress = {
  id: ID;
  label: AddressLabel;
  fullName: string;
  phoneNumber: string;
  addressLine: string;
  provinceCode: string | null;
  districtCode: string | null;
  wardCode: string | null;
  postalCode: string | null;
  countryCode: string;
  isDefault: boolean;
};
export type AddressInput = {
  label?: AddressLabel;
  fullName: string;
  phoneNumber: string;
  addressLine: string;
  provinceCode?: string | null;
  districtCode?: string | null;
  wardCode?: string | null;
  postalCode?: string | null;
  countryCode?: string;
};

export type WishlistPage = { total: number; items: { addedAt: DateTime; product: ProductCard }[] };

export type ReviewSort = 'HELPFUL' | 'NEWEST' | 'RATING_HIGH' | 'RATING_LOW';
export type Review = {
  id: ID;
  rating: number;
  title: string | null;
  content: string | null;
  mediaUrls: string[];
  authorName: string;
  verifiedPurchase: boolean;
  helpfulCount: number;
  sellerReply: string | null;
  createdAt: DateTime;
};
export type ReviewPage = { total: number; items: Review[] };

export type NotificationCategory =
  'INVENTORY' | 'MESSAGE' | 'ORDER' | 'PAYMENT' | 'PROMOTION' | 'REVIEW' | 'SYSTEM' | 'WISHLIST';
export type Notification = {
  id: ID;
  type: string;
  category: NotificationCategory;
  title: string;
  body: string;
  link: string | null;
  readAt: DateTime | null;
  createdAt: DateTime;
};
export type NotificationPage = { total: number; items: Notification[] };

export type OrderSummary = { orderCode: string; status: string; itemCount: number; total: Money; placedAt: DateTime };
export type Order = {
  orderId: ID;
  orderCode: string;
  status: string;
  placedAt: DateTime;
  paymentMethod: string | null;
  promotionCode: string | null;
  note: string | null;
  cancelReason: string | null;
  subtotal: Money;
  discount: Money;
  total: Money;
  shippingAddress: {
    fullName: string;
    phone: string;
    line1: string;
    line2: string | null;
    ward: string | null;
    district: string | null;
    city: string;
    country: string;
    postalCode: string | null;
  } | null;
  lines: {
    name: string;
    sku: string | null;
    quantity: number;
    unitPrice: Money;
    total: Money;
    product: Pick<ProductCard, 'id' | 'slug' | 'thumbnail'> | null;
  }[];
};

export type PaymentMethod = 'CASH_ON_DELIVERY' | 'CREDIT_CARD' | 'MOMO' | 'PAYPAL' | 'STRIPE' | 'VN_PAY';
export type CheckoutInput = {
  idempotencyKey: string;
  paymentMethod: PaymentMethod;
  shippingAddressId?: ID | null;
  promotionCode?: string | null;
  note?: string | null;
  returnUrl?: string | null;
  paymentToken?: string | null;
};
export type CheckoutResult = {
  orderId: ID;
  orderCode: string;
  status: string;
  total: Money;
  discount: Money;
  checkoutUrl: string;
};
export type CheckoutProgress = {
  orderCode: string;
  orderStatus: string;
  step: string | null;
  failureReason: string | null;
  paymentRedirectUrl: string | null;
};
export type PromotionQuote = {
  valid: boolean;
  reason: string | null;
  subtotal: Money;
  discount: Money | null;
  total: Money;
};
export type SearchEntry = { keyword: string; searchedAt: DateTime };
