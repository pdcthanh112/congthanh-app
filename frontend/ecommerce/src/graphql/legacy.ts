/**
 * LỚP CHUYỂN TIẾP, SẼ BỎ. Kế hoạch:
 *  1. Code mới dùng thẳng kiểu ở ./types qua src/api (đã làm: cart, address, notification, order, checkout).
 *  2. Còn dùng lớp này: productApi/categoryApi/wishlistApi/reviewApi (toProduct, toCategory, toReview...)
 *     và envelope ok()/paged() mà các trang danh sách đang đọc (`.data.responseList`, `.paginationInfo`).
 *  3. Khi chuyển nốt các trang đó sang kiểu GraphQL (ProductCard, Category, Review), xoá file này và models/types cũ.
 *
 * Chuyển kết quả GraphQL sang model cũ của UI (models/types) và envelope {data, message, statusCode},
 * để component hiện có chạy tiếp trong lúc chuyển dần sang dùng thẳng kiểu ở ./types.
 *
 * Lưu ý: model cũ khai báo id dạng number, còn GraphQL trả ID dạng chuỗi (UUID). legacyId() giữ nguyên giá trị
 * chuỗi lúc chạy (chỉ dùng làm key/tham số truyền lại cho API), các hàm ở src/api nhận cả string | number.
 */
import type {
  Cart,
  Category,
  Product,
  Response,
  ResponseWithPagination,
} from '@/models/types';
import type { Review, ReviewStatistic } from '@/models/types/Review';
import type * as G from './types';

/** ID chuỗi của GraphQL đặt vào trường id: number của model cũ (xem ghi chú đầu file). */
const legacyId = (id: string) => id as unknown as number;

export const ok = <T>(data: T, message = 'Success'): Response<T> => ({ data, message, statusCode: 200 });

const pages = (total: number, limit: number) => Math.max(1, Math.ceil(total / Math.max(limit, 1)));

export const paged = <T>(items: T[], page: number, limit: number, total: number): ResponseWithPagination<T> => ({
  data: {
    responseList: items,
    paginationInfo: { page, limit, totalPage: pages(total, limit), totalElement: total },
    totalPage: pages(total, limit),
  },
  message: 'Success',
  statusCode: 200,
});

export function toProduct(p: G.ProductCard | G.Product): Product {
  const full = 'sku' in p ? p : undefined;
  return {
    id: p.id,
    name: p.name,
    slug: p.slug ?? p.id,
    category: full?.categories ?? [],
    description: full?.description ?? '',
    supplier: full?.sellerId ?? '',
    thumbnail: { id: 0, product: p.id, imagePath: p.thumbnail ?? '' },
    image: (full?.images ?? []).map((imagePath, i) => ({ id: i, product: p.id, imagePath })),
    parent: '',
    hasVariant: false,
    brand: full?.brandName ?? '',
    status: full && !full.active ? 'INACTIVE' : 'ACTIVE',
    variant: [],
    attribute: [],
    metadata: { title: p.name, keywords: '', description: full?.description ?? '' },
    price: p.price,
    originalPrice: p.originalPrice,
    discountPercent: p.discountPercent,
    ratingSummary: p.ratingSummary,
    inWishlist: p.inWishlist,
    stockStatus: full?.stock?.status,
  };
}

export function toCart(c: G.Cart, customerId = ''): Cart {
  return {
    id: legacyId(c.id ?? 'current'),
    product: '',
    quantity: c.itemCount,
    name: 'default',
    customerId,
    status: c.checkoutReady ? 'ACTIVE' : 'NEEDS_REVIEW',
    createdDate: new Date(),
    checkoutDate: new Date(),
    cartItems: c.lines.map((l) => ({
      id: legacyId(l.id),
      quantity: l.quantity,
      product: toProduct({
        id: l.product?.id ?? '',
        name: l.product?.name ?? '',
        slug: l.product?.slug ?? null,
        thumbnail: l.product?.thumbnail ?? null,
        price: l.unitPrice ?? { amount: 0, currency: 'VND', formatted: '' },
        originalPrice: l.unitPrice ?? { amount: 0, currency: 'VND', formatted: '' },
        discountPercent: 0,
        ratingSummary: { average: 0, count: 0 },
        inWishlist: false,
      }),
    })),
  };
}

export const toCategory = (c: G.Category): Category => ({
  id: c.id,
  name: c.name,
  slug: c.slug,
  description: c.description ?? '',
  children: c.children.map((x) => ({ ...x, description: '', children: [] })),
});

export const toReview = (r: G.Review, productId: string): Review => ({
  id: legacyId(r.id),
  content: r.content ?? '',
  rating: r.rating,
  customerId: r.authorName,
  product: productId,
  reviewMedia: r.mediaUrls.map((url, i) => ({ id: i, url })),
  createdAt: new Date(r.createdAt),
});

export const toReviewStatistic = (s: G.RatingSummary, withMedia = 0): ReviewStatistic => ({
  totalReview: s.count,
  averageRating: s.average,
  // stars[i] = số đánh giá (i+1) sao
  reviewRating1Star: s.stars[0] ?? 0,
  reviewRating2Star: s.stars[1] ?? 0,
  reviewRating3Star: s.stars[2] ?? 0,
  reviewRating4Star: s.stars[3] ?? 0,
  reviewRating5Star: s.stars[4] ?? 0,
  reviewWithMedia: withMedia,
});

