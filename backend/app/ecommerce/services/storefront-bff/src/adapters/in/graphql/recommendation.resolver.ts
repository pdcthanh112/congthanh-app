import { CurrentPrincipal, type Principal, Public } from '@ecommerce/platform';
import { Args, Context, ID, Int, Mutation, Parent, Query, ResolveField, Resolver } from '@nestjs/graphql';
import { StorefrontService } from '../../../application/storefront.service.js';
import type { GqlCtx } from './loaders.js';
import { recommendation } from './mappers.js';
import { ProductModel, type ProductSource, RecommendationKindGql, RecommendationModel } from './types.js';

/** Cookie/ID ẩn danh do web sinh cho khách chưa đăng nhập (gửi qua header X-Session-Id). */
function sessionId(ctx: GqlCtx): string | undefined {
  const v = ctx.req?.headers['x-session-id'];
  const id = Array.isArray(v) ? v[0] : v;
  return id && /^[A-Za-z0-9_-]{8,64}$/.test(id) ? id : undefined;
}

/** Gợi ý sản phẩm (recommendation-service). Lỗi phía sau trả danh sách rỗng, không làm hỏng trang. */
@Resolver(() => ProductModel)
export class RecommendationResolver {
  constructor(private readonly svc: StorefrontService) {}

  @ResolveField(() => [RecommendationModel], { description: 'Thường mua kèm hoặc sản phẩm tương tự' })
  async recommendations(
    @Parent() p: ProductSource,
    @Args('kind', { type: () => RecommendationKindGql, defaultValue: RecommendationKindGql.BOUGHT_TOGETHER })
    kind: RecommendationKindGql,
    @Args('limit', { type: () => Int, defaultValue: 8 }) limit: number,
  ) {
    return (await this.svc.recommendationsFor(p.id, kind, limit)).map(recommendation);
  }

  @Query(() => [RecommendationModel], { description: 'Gợi ý cho riêng khách (vãng lai: theo phiên, hoặc xu hướng)' })
  @Public()
  async recommendedForYou(
    @CurrentPrincipal() p: Principal | undefined,
    @Context() ctx: GqlCtx,
    @Args('limit', { type: () => Int, defaultValue: 12 }) limit: number,
  ) {
    return (await this.svc.forYou(p, limit, sessionId(ctx))).map(recommendation);
  }

  @Query(() => [RecommendationModel], { description: 'Sản phẩm đang được quan tâm (7 ngày gần đây)' })
  @Public()
  async trendingProducts(
    @Args('limit', { type: () => Int, defaultValue: 12 }) limit: number,
    @Args('categoryId', { type: () => ID, nullable: true }) categoryId?: string,
  ) {
    return (await this.svc.trending(limit, categoryId)).map(recommendation);
  }

  @Mutation(() => Boolean, { description: 'Ghi nhận khách xem sản phẩm (tín hiệu cho gợi ý)' })
  @Public()
  async trackProductView(
    @CurrentPrincipal() p: Principal | undefined,
    @Context() ctx: GqlCtx,
    @Args('productId', { type: () => ID }) productId: string,
  ) {
    return this.svc.trackView(p, productId, sessionId(ctx));
  }
}
