import { gql } from '@/graphql/client';
import { ProductReviewsQuery } from '@/graphql/documents';
import { ok, paged, toReview, toReviewStatistic } from '@/graphql/legacy';
import { Response, ResponseWithPagination } from '@/models/types';
import { Review, ReviewStatistic } from '@/models/types/Review';

// Đánh giá (review-service, qua supergraph: Product.ratingSummary / Product.reviews).
export const getStatisticFromProduct = async (productId: string): Promise<Response<ReviewStatistic>> => {
  const { product } = await gql(ProductReviewsQuery, { productId, size: 1 });
  const withMedia =
    (await gql(ProductReviewsQuery, { productId, size: 1, withMedia: true })).product?.reviews.total ?? 0;
  return ok(toReviewStatistic(product?.ratingSummary ?? { average: 0, count: 0, stars: [] }, withMedia));
};

export const getReviewByProduct = async (
  productId: string,
  filter: { rating?: number | null; hasMedia?: boolean },
  pagination: { page: number; limit?: number }
): Promise<ResponseWithPagination<Review>> => {
  const size = pagination.limit ?? 5;
  const { product } = await gql(ProductReviewsQuery, {
    productId,
    page: pagination.page,
    size,
    rating: filter.rating || null,
    withMedia: Boolean(filter.hasMedia),
  });
  const reviews = product?.reviews ?? { total: 0, items: [] };
  return paged(
    reviews.items.map((r) => toReview(r, productId)),
    pagination.page,
    size,
    reviews.total
  );
};
