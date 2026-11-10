import { CurrentPrincipal, hasAnyRole, type Principal, Role, Roles } from '@ecom/platform';
import {
  Args,
  Context,
  ID,
  Int,
  Mutation,
  Parent,
  Query,
  ResolveField,
  ResolveReference,
  Resolver,
} from '@nestjs/graphql';
import DataLoader from 'dataloader';
import { ReviewService } from '../../../application/review.service.js';
import { averageOf, countOf, type RatingSummary } from '../../../domain/rating-summary.js';
import { maskName, type Review } from '../../../domain/review.js';
import {
  EditReviewInput,
  PendingReviewModel,
  ProductRef,
  RatingSummaryModel,
  ReviewModel,
  ReviewPageModel,
  ReviewSortGql,
  ReviewStatusGql,
  SubmitReviewInput,
} from './types.js';

/** Người xem là tác giả hoặc admin thì thấy đủ; người khác thấy tên đã che và không thấy lý do ẩn. */
function toModel(r: Review, viewer: Principal | undefined): ReviewModel {
  const s = r.snapshot();
  const privileged = !!viewer && (viewer.id === s.customerId || hasAnyRole(viewer, [Role.ADMIN, Role.SUPPORT]));
  return {
    ...s,
    product: { id: s.productId },
    authorName: privileged ? s.authorName : maskName(s.authorName),
    verifiedPurchase: true,
    status: s.status as ReviewStatusGql,
    moderationReason: privileged ? s.moderationReason : null,
  };
}

const toSummaryModel = (s: RatingSummary): RatingSummaryModel => ({
  average: averageOf(s),
  count: countOf(s),
  stars: [...s.stars],
});

const authorOf = (p: Principal) => ({ id: p.id, name: p.name ?? p.username ?? 'Khách hàng' });

type Ctx = { summaryLoader?: DataLoader<string, RatingSummary> };

/** Product (chủ: storefront-bff) được review-service góp `reviews` và `ratingSummary`. */
@Resolver(() => ProductRef)
export class ProductReviewsResolver {
  constructor(private readonly service: ReviewService) {}

  @ResolveReference()
  resolveReference(ref: { id: string }): ProductRef {
    return { id: ref.id };
  }

  @ResolveField(() => RatingSummaryModel)
  async ratingSummary(@Parent() p: ProductRef, @Context() ctx: Ctx) {
    ctx.summaryLoader ??= new DataLoader(async (ids: readonly string[]) => {
      const map = await this.service.read.summaries.getMany(ids);
      return ids.map((id) => map.get(id)!);
    });
    return toSummaryModel(await ctx.summaryLoader.load(p.id));
  }

  @ResolveField(() => ReviewPageModel)
  async reviews(
    @Parent() p: ProductRef,
    @CurrentPrincipal() viewer: Principal | undefined,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 10 }) size: number,
    @Args('rating', { type: () => Int, nullable: true }) rating?: number,
    @Args('withMedia', { defaultValue: false }) withMedia?: boolean,
    @Args('sort', { type: () => ReviewSortGql, defaultValue: ReviewSortGql.HELPFUL }) sort?: ReviewSortGql,
  ): Promise<ReviewPageModel> {
    const res = await this.service.search({
      productId: p.id,
      ...(rating ? { rating } : {}),
      withMedia: !!withMedia,
      sort: sort ?? ReviewSortGql.HELPFUL,
      limit: size,
      offset: (Math.max(page, 1) - 1) * size,
    });
    return { total: res.total, items: res.items.map((r) => toModel(r, viewer)) };
  }
}

@Resolver(() => ReviewModel)
export class ReviewResolver {
  constructor(private readonly reviews: ReviewService) {}

  @ResolveReference()
  async resolveReference(ref: { id: string }) {
    const r = await this.reviews.read.reviews.findById(ref.id);
    // Tham chiếu từ subgraph khác (ví dụ thông báo): chỉ đánh giá đang hiển thị, tên đã che.
    return r && r.status === 'PUBLISHED' ? toModel(r, undefined) : null;
  }

  @Query(() => [PendingReviewModel], { description: 'Sản phẩm đã mua đang chờ khách đánh giá' })
  async myPendingReviews(
    @CurrentPrincipal() p: Principal,
    @Args('limit', { type: () => Int, defaultValue: 20 }) limit: number,
  ): Promise<PendingReviewModel[]> {
    const rows = await this.reviews.pendingReviews(p.id, limit);
    return rows.map((r) => ({ orderId: r.orderId, product: { id: r.productId }, purchasedAt: r.confirmedAt }));
  }

  @Query(() => ReviewPageModel)
  async myReviews(
    @CurrentPrincipal() p: Principal,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 10 }) size: number,
  ): Promise<ReviewPageModel> {
    const res = await this.reviews.search({
      customerId: p.id,
      status: 'ALL',
      sort: 'NEWEST',
      limit: size,
      offset: (Math.max(page, 1) - 1) * size,
    });
    return { total: res.total, items: res.items.map((r) => toModel(r, p)) };
  }

  @Query(() => ReviewPageModel, { description: 'Backoffice: lọc đánh giá để kiểm duyệt' })
  @Roles(Role.ADMIN, Role.SUPPORT)
  async adminReviews(
    @CurrentPrincipal() p: Principal,
    @Args('productId', { type: () => ID, nullable: true }) productId?: string,
    @Args('status', { type: () => ReviewStatusGql, nullable: true }) status?: ReviewStatusGql,
    @Args('rating', { type: () => Int, nullable: true }) rating?: number,
    @Args('page', { type: () => Int, defaultValue: 1 }) page = 1,
    @Args('size', { type: () => Int, defaultValue: 20 }) size = 20,
  ): Promise<ReviewPageModel> {
    const res = await this.reviews.search({
      ...(productId ? { productId } : {}),
      ...(rating ? { rating } : {}),
      status: status ?? 'ALL',
      sort: 'NEWEST',
      limit: size,
      offset: (Math.max(page, 1) - 1) * size,
    });
    return { total: res.total, items: res.items.map((r) => toModel(r, p)) };
  }

  @Mutation(() => ReviewModel)
  @Roles(Role.CUSTOMER)
  async submitReview(@CurrentPrincipal() p: Principal, @Args('input') i: SubmitReviewInput) {
    const r = await this.reviews.submit(authorOf(p), i.orderId, i.productId, i);
    return toModel(r, p);
  }

  @Mutation(() => ReviewModel)
  async editReview(
    @CurrentPrincipal() p: Principal,
    @Args('id', { type: () => ID }) id: string,
    @Args('version', { type: () => Int }) version: number,
    @Args('input') i: EditReviewInput,
  ) {
    return toModel(await this.reviews.edit(p.id, id, version, i), p);
  }

  @Mutation(() => Boolean)
  deleteMyReview(@CurrentPrincipal() p: Principal, @Args('id', { type: () => ID }) id: string) {
    return this.reviews.remove(p.id, id);
  }

  @Mutation(() => Boolean, { description: 'Đánh dấu "hữu ích" (mỗi người một lần)' })
  markReviewHelpful(@CurrentPrincipal() p: Principal, @Args('id', { type: () => ID }) id: string) {
    return this.reviews.voteHelpful(p.id, id);
  }

  @Mutation(() => ReviewModel)
  @Roles(Role.SELLER, Role.ADMIN)
  async replyToReview(
    @CurrentPrincipal() p: Principal,
    @Args('id', { type: () => ID }) id: string,
    @Args('version', { type: () => Int }) version: number,
    @Args('reply') reply: string,
  ) {
    return toModel(await this.reviews.reply(p.id, hasAnyRole(p, [Role.ADMIN]), id, version, reply), p);
  }

  @Mutation(() => ReviewModel)
  @Roles(Role.ADMIN, Role.SUPPORT)
  async moderateReview(
    @CurrentPrincipal() p: Principal,
    @Args('id', { type: () => ID }) id: string,
    @Args('version', { type: () => Int }) version: number,
    @Args('status', { type: () => ReviewStatusGql }) status: ReviewStatusGql,
    @Args('reason', { type: () => String, nullable: true }) reason: string | null,
  ) {
    return toModel(await this.reviews.moderate(p.id, id, version, status, reason), p);
  }
}
