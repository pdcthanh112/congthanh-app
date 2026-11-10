/**
 * Mọi truy vấn GraphQL của storefront. Mỗi document mang kiểu kết quả và biến (phantom type) để gql() suy ra.
 * `npm run graphql:check` parse từng document và validate với supergraph storefront; sai tên trường thì CI hỏng.
 */
import type * as T from './types';

export type TypedDocument<TResult, TVariables> = string & {
  readonly __result?: TResult;
  readonly __variables?: TVariables;
};
type NoVars = Record<string, never>;
const doc = <TResult, TVariables = NoVars>(source: string) => source as TypedDocument<TResult, TVariables>;

const MONEY = 'amount currency formatted';
const PRODUCT_CARD = `id name slug thumbnail price { ${MONEY} } originalPrice { ${MONEY} } discountPercent ratingSummary { average count } inWishlist`;
const CATEGORY = 'id name slug description image parentId sortOrder children { id name slug }';
const CART = `id itemCount checkoutReady subtotal { ${MONEY} } lines { id quantity purchasable issue unitPrice { ${MONEY} } lineTotal { ${MONEY} } product { id name slug thumbnail } }`;
const ADDRESS =
  'id label fullName phoneNumber addressLine provinceCode districtCode wardCode postalCode countryCode isDefault';
const REVIEW = 'id rating title content mediaUrls authorName verifiedPurchase helpfulCount sellerReply createdAt';
const NOTIFICATION = 'id type category title body link readAt createdAt';

// ---------- sản phẩm, danh mục ----------
export const ProductsQuery = doc<
  { products: T.ProductPage },
  { categoryId?: T.ID | null; page?: number; limit?: number }
>(
  `query Products($categoryId: ID, $page: Int! = 1, $limit: Int! = 20) {
    products(categoryId: $categoryId, page: $page, limit: $limit) { page limit totalItems totalPages items { ${PRODUCT_CARD} } }
  }`
);

export const ProductQuery = doc<{ product: T.Product | null }, { id?: T.ID; slug?: string }>(
  `query Product($id: ID, $slug: String) {
    product(id: $id, slug: $slug) {
      ${PRODUCT_CARD} sku description images brandName sellerId active
      stock { available status } categories { id name slug } tags { id name }
      ratingSummary { average count stars }
    }
  }`
);

export const SearchProductsQuery = doc<{ searchProducts: T.ProductCard[] }, { keyword: string }>(
  `query SearchProducts($keyword: String!) { searchProducts(keyword: $keyword) { ${PRODUCT_CARD} } }`
);

export const CategoriesQuery = doc<{ categories: T.Category[] }, { parentId?: T.ID | null }>(
  `query Categories($parentId: ID) { categories(parentId: $parentId) { ${CATEGORY} } }`
);

export const CategoryQuery = doc<{ category: T.Category | null }, { id?: T.ID; slug?: string }>(
  `query Category($id: ID, $slug: String) { category(id: $id, slug: $slug) { ${CATEGORY} } }`
);

// ---------- giỏ hàng ----------
export const MyCartQuery = doc<{ myCart: T.Cart }>(`query MyCart { myCart { ${CART} } }`);
export const AddToCartMutation = doc<{ addToCart: T.Cart }, { productId: T.ID; quantity: number }>(
  `mutation AddToCart($productId: ID!, $quantity: Int!) { addToCart(productId: $productId, quantity: $quantity) { ${CART} } }`
);
export const UpdateCartItemMutation = doc<{ updateCartItem: T.Cart }, { itemId: T.ID; quantity: number }>(
  `mutation UpdateCartItem($itemId: ID!, $quantity: Int!) { updateCartItem(itemId: $itemId, quantity: $quantity) { ${CART} } }`
);
export const RemoveCartItemMutation = doc<{ removeCartItem: T.Cart }, { itemId: T.ID }>(
  `mutation RemoveCartItem($itemId: ID!) { removeCartItem(itemId: $itemId) { ${CART} } }`
);
export const ClearCartMutation = doc<{ clearCart: T.Cart }>(`mutation ClearCart { clearCart { ${CART} } }`);

// ---------- khách hàng: địa chỉ, wishlist, lịch sử tìm kiếm ----------
export const MyAddressesQuery = doc<{
  me: { id: T.ID; shippingAddresses: T.ShippingAddress[]; defaultShippingAddress: T.ShippingAddress | null };
}>(`query MyAddresses { me { id shippingAddresses { ${ADDRESS} } defaultShippingAddress { ${ADDRESS} } } }`);
export const AddAddressMutation = doc<
  { addShippingAddress: T.ShippingAddress },
  { input: T.AddressInput; makeDefault?: boolean }
>(
  `mutation AddAddress($input: AddressInput!, $makeDefault: Boolean! = false) { addShippingAddress(input: $input, makeDefault: $makeDefault) { ${ADDRESS} } }`
);
export const UpdateAddressMutation = doc<
  { updateShippingAddress: T.ShippingAddress },
  { id: T.ID; input: T.AddressInput }
>(
  `mutation UpdateAddress($id: ID!, $input: AddressInput!) { updateShippingAddress(id: $id, input: $input) { ${ADDRESS} } }`
);
export const DeleteAddressMutation = doc<{ deleteShippingAddress: boolean }, { id: T.ID }>(
  `mutation DeleteAddress($id: ID!) { deleteShippingAddress(id: $id) }`
);
export const SetDefaultAddressMutation = doc<{ setDefaultShippingAddress: T.ShippingAddress }, { id: T.ID }>(
  `mutation SetDefaultAddress($id: ID!) { setDefaultShippingAddress(id: $id) { ${ADDRESS} } }`
);

export const MyWishlistQuery = doc<{ me: { id: T.ID; wishlist: T.WishlistPage } }, { page?: number; size?: number }>(
  `query MyWishlist($page: Int! = 1, $size: Int! = 20) { me { id wishlist(page: $page, size: $size) { total items { addedAt product { ${PRODUCT_CARD} } } } } }`
);
export const AddToWishlistMutation = doc<{ addToWishlist: boolean }, { productId: T.ID }>(
  `mutation AddToWishlist($productId: ID!) { addToWishlist(productId: $productId) }`
);
export const RemoveFromWishlistMutation = doc<{ removeFromWishlist: boolean }, { productId: T.ID }>(
  `mutation RemoveFromWishlist($productId: ID!) { removeFromWishlist(productId: $productId) }`
);

export const RecentSearchesQuery = doc<{ me: { id: T.ID; recentSearches: T.SearchEntry[] } }, { limit?: number }>(
  `query RecentSearches($limit: Int! = 10) { me { id recentSearches(limit: $limit) { keyword searchedAt } } }`
);
export const RecordSearchMutation = doc<{ recordSearch: boolean }, { keyword: string }>(
  `mutation RecordSearch($keyword: String!) { recordSearch(keyword: $keyword) }`
);
export const RemoveSearchMutation = doc<{ removeSearch: boolean }, { keyword: string }>(
  `mutation RemoveSearch($keyword: String!) { removeSearch(keyword: $keyword) }`
);

// ---------- đánh giá ----------
export const ProductReviewsQuery = doc<
  { product: { id: T.ID; ratingSummary: T.RatingSummary; reviews: T.ReviewPage } | null },
  { productId: T.ID; page?: number; size?: number; rating?: number | null; withMedia?: boolean; sort?: T.ReviewSort }
>(
  `query ProductReviews($productId: ID!, $page: Int! = 1, $size: Int! = 5, $rating: Int, $withMedia: Boolean! = false, $sort: ReviewSort! = HELPFUL) {
    product(id: $productId) {
      id ratingSummary { average count stars }
      reviews(page: $page, size: $size, rating: $rating, withMedia: $withMedia, sort: $sort) { total items { ${REVIEW} } }
    }
  }`
);

// ---------- thông báo ----------
export const MyNotificationsQuery = doc<
  { myNotifications: T.NotificationPage; unreadNotificationCount: number },
  { page?: number; size?: number; unreadOnly?: boolean }
>(
  `query MyNotifications($page: Int! = 1, $size: Int! = 20, $unreadOnly: Boolean! = false) {
    myNotifications(page: $page, size: $size, unreadOnly: $unreadOnly) { total items { ${NOTIFICATION} } }
    unreadNotificationCount
  }`
);
export const MarkNotificationsReadMutation = doc<{ markNotificationsRead: number }, { ids?: T.ID[] | null }>(
  `mutation MarkNotificationsRead($ids: [ID!]) { markNotificationsRead(ids: $ids) }`
);
export const NotificationAddedSubscription = doc<{ notificationAdded: T.Notification }>(
  `subscription NotificationAdded { notificationAdded { ${NOTIFICATION} } }`
);

// ---------- đơn hàng, thanh toán ----------
export const MyOrdersQuery = doc<{ myOrders: T.OrderSummary[] }, { limit?: number }>(
  `query MyOrders($limit: Int! = 20) { myOrders(limit: $limit) { orderCode status itemCount placedAt total { ${MONEY} } } }`
);
export const OrderQuery = doc<{ order: T.Order }, { orderCode: string }>(
  `query Order($orderCode: String!) {
    order(orderCode: $orderCode) {
      orderId orderCode status placedAt paymentMethod promotionCode note cancelReason
      subtotal { ${MONEY} } discount { ${MONEY} } total { ${MONEY} }
      shippingAddress { fullName phone line1 line2 ward district city country postalCode }
      lines { name sku quantity unitPrice { ${MONEY} } total { ${MONEY} } product { id slug thumbnail } }
    }
  }`
);
export const CancelOrderMutation = doc<
  { cancelOrder: Pick<T.Order, 'orderCode' | 'status'> },
  { orderCode: string; reason?: string | null }
>(
  `mutation CancelOrder($orderCode: String!, $reason: String) { cancelOrder(orderCode: $orderCode, reason: $reason) { orderCode status } }`
);
export const QuotePromotionQuery = doc<{ quotePromotion: T.PromotionQuote }, { code: string }>(
  `query QuotePromotion($code: String!) { quotePromotion(code: $code) { valid reason subtotal { ${MONEY} } discount { ${MONEY} } total { ${MONEY} } } }`
);
export const CheckoutMutation = doc<{ checkout: T.CheckoutResult }, { input: T.CheckoutInput }>(
  `mutation Checkout($input: CheckoutInput!) {
    checkout(input: $input) { orderId orderCode status checkoutUrl total { ${MONEY} } discount { ${MONEY} } }
  }`
);
export const CheckoutProgressQuery = doc<{ checkoutProgress: T.CheckoutProgress }, { orderCode: string }>(
  `query CheckoutProgress($orderCode: String!) { checkoutProgress(orderCode: $orderCode) { orderCode orderStatus step failureReason paymentRedirectUrl } }`
);
