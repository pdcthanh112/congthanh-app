import type { ProductDetail, ProductSummary } from '@ecom/clients';
import { DomainError, Role, Roles } from '@ecom/platform';
import { Args, Context, ID, Int, Parent, Query, ResolveField, ResolveReference, Resolver } from '@nestjs/graphql';
import { BackofficeService } from '../../../application/backoffice.service.js';
import { type GqlCtx, loadersOf } from './loaders.js';
import { money, stockLevel } from './mappers.js';
import {
  CategoryRef,
  MoneyModel,
  ProductModel,
  ProductPageModel,
  type ProductSource,
  StockLevelModel,
} from './types.js';

const STAFF = [Role.ADMIN, Role.SELLER, Role.SUPPORT];

@Resolver(() => ProductModel)
export class ProductResolver {
  constructor(private readonly svc: BackofficeService) {}

  @Query(() => ProductModel, { nullable: true, description: 'Theo id hoặc slug (đúng một trong hai)' })
  @Roles(...STAFF)
  async adminProduct(
    @Context() ctx: GqlCtx,
    @Args('id', { type: () => ID, nullable: true }) id?: string,
    @Args('slug', { nullable: true }) slug?: string,
  ): Promise<ProductSource | null> {
    if (!!id === !!slug) throw DomainError.invalid('provide exactly one of id or slug');
    const loaders = loadersOf(ctx, this.svc);
    if (id) return (await loaders.summary.load(id)) ? { id } : null;
    try {
      const d = await this.svc.products.bySlug(slug as string);
      loaders.detail.prime(d.id, d);
      return { id: d.id };
    } catch (e) {
      if (e instanceof DomainError && e.kind === 'NOT_FOUND') return null;
      throw e;
    }
  }

  @Query(() => ProductPageModel)
  @Roles(Role.ADMIN, Role.SELLER)
  async adminProducts(
    @Context() ctx: GqlCtx,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('limit', { type: () => Int, defaultValue: 50 }) limit: number,
    @Args('categoryId', { type: () => ID, nullable: true }) categoryId?: string,
  ): Promise<ProductPageModel> {
    const size = Math.min(Math.max(limit, 1), 200);
    const p = Math.max(page, 1);
    const res = categoryId
      ? await this.svc.products.byCategory(categoryId, p, size)
      : await this.svc.products.list(p, size);
    const loaders = loadersOf(ctx, this.svc);
    for (const d of res.responseList) loaders.detail.prime(d.id, d);
    return {
      items: res.responseList.map((d) => ({ id: d.id })),
      page: res.paginationInfo.page,
      limit: res.paginationInfo.limit,
      totalPages: res.paginationInfo.totalPage,
      totalItems: res.paginationInfo.totalElement,
    };
  }

  @Query(() => [StockLevelModel], { description: 'Tồn kho theo danh sách sản phẩm (tối đa 200)' })
  @Roles(Role.ADMIN, Role.SELLER)
  async inventoryLevels(@Context() ctx: GqlCtx, @Args('productIds', { type: () => [ID] }) productIds: string[]) {
    if (productIds.length > 200) throw DomainError.invalid('at most 200 product ids');
    const levels = await loadersOf(ctx, this.svc).stock.loadMany(productIds);
    return levels.flatMap((l) => (l && !(l instanceof Error) ? [stockLevel(l)] : []));
  }

  @ResolveReference()
  resolveReference(ref: { id: string }): ProductSource {
    return { id: ref.id };
  }

  @ResolveField(() => String) async sku(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.summary(p, ctx)).sku;
  }

  @ResolveField(() => String) async name(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.summary(p, ctx)).name;
  }

  @ResolveField(() => MoneyModel) async price(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return money((await this.summary(p, ctx)).price);
  }

  @ResolveField(() => MoneyModel) async originalPrice(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return money((await this.summary(p, ctx)).originalPrice);
  }

  @ResolveField(() => Boolean) async active(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.summary(p, ctx)).active;
  }

  @ResolveField(() => ID) async sellerId(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.summary(p, ctx)).sellerId;
  }

  @ResolveField(() => String, { nullable: true }) async slug(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).slug ?? null;
  }

  @ResolveField(() => String, { nullable: true }) async status(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).status ?? null;
  }

  @ResolveField(() => String, { nullable: true }) async thumbnail(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).thumbnail ?? null;
  }

  @ResolveField(() => String, { nullable: true }) async brandName(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).brand ?? null;
  }

  @ResolveField(() => [CategoryRef]) async categories(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return ((await this.detail(p, ctx)).category ?? []).map((c) => ({ id: c.categoryId }));
  }

  @ResolveField(() => StockLevelModel, { nullable: true }) async stock(
    @Parent() p: ProductSource,
    @Context() ctx: GqlCtx,
  ) {
    const l = await loadersOf(ctx, this.svc).stock.load(p.id);
    return l ? stockLevel(l) : null;
  }

  private async summary(p: ProductSource, ctx: GqlCtx): Promise<ProductSummary> {
    const s = await loadersOf(ctx, this.svc).summary.load(p.id);
    if (!s) throw DomainError.notFound(`product ${p.id} not found`);
    return s;
  }

  private detail(p: ProductSource, ctx: GqlCtx): Promise<ProductDetail> {
    return loadersOf(ctx, this.svc).detail.load(p.id);
  }
}
