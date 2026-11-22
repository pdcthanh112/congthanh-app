import type { ProductDetail, ProductSummary } from '@ecommerce/clients';
import { DomainError, Public } from '@ecommerce/platform';
import { Args, Context, ID, Int, Parent, Query, ResolveField, ResolveReference, Resolver } from '@nestjs/graphql';
import { StorefrontService } from '../../../application/storefront.service.js';
import { type GqlCtx, loadersOf } from './loaders.js';
import { discountPercent, money, stock } from './mappers.js';
import {
  CategoryRef,
  type FacetFilterInput,
  MoneyModel,
  ProductModel,
  ProductPageModel,
  ProductSearchInput,
  ProductSearchResultModel,
  type ProductSource,
  StockModel,
} from './types.js';

const MAX_PAGE_SIZE = 100;

@Resolver(() => ProductModel)
export class ProductResolver {
  constructor(private readonly svc: StorefrontService) {}

  @Query(() => ProductModel, { nullable: true, description: 'Tìm theo id hoặc slug (đúng một trong hai)' })
  @Public()
  async product(
    @Context() ctx: GqlCtx,
    @Args('id', { type: () => ID, nullable: true }) id?: string,
    @Args('slug', { nullable: true }) slug?: string,
  ): Promise<ProductSource | null> {
    if (!!id === !!slug) throw DomainError.invalid('provide exactly one of id or slug');
    const loaders = loadersOf(ctx, this.svc);
    if (id) return (await loaders.summary.load(id)) ? { id } : null;
    try {
      const detail = await this.svc.products.bySlug(slug as string);
      loaders.detail.prime(detail.id, detail);
      return { id: detail.id };
    } catch (e) {
      if (e instanceof DomainError && e.kind === 'NOT_FOUND') return null;
      throw e;
    }
  }

  @Query(() => ProductPageModel, { description: 'Danh sách sản phẩm, lọc theo danh mục nếu có' })
  @Public()
  async products(
    @Context() ctx: GqlCtx,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('limit', { type: () => Int, defaultValue: 20 }) limit: number,
    @Args('categoryId', { type: () => ID, nullable: true }) categoryId?: string,
  ): Promise<ProductPageModel> {
    const size = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);
    const p = Math.max(page, 1);
    const res = categoryId
      ? await this.svc.products.byCategory(categoryId, p, size)
      : await this.svc.products.list(p, size);
    return {
      items: this.prime(ctx, res.responseList),
      page: res.paginationInfo.page,
      limit: res.paginationInfo.limit,
      totalPages: res.paginationInfo.totalPage,
      totalItems: res.paginationInfo.totalElement,
    };
  }

  @Query(() => [ProductModel], { description: 'Tìm theo từ khoá (tên, SKU, thuộc tính tìm được) qua search-service' })
  @Public()
  async searchProducts(@Args('keyword') keyword: string): Promise<ProductSource[]> {
    const k = keyword.trim();
    if (k.length < 2) return [];
    const res = await this.svc.search.search({ text: k.slice(0, 100), size: MAX_PAGE_SIZE });
    return res.hits.map((h) => ({ id: h.id }));
  }

  @Query(() => ProductSearchResultModel, {
    description: 'Tìm kiếm có lọc thuộc tính/option/thương hiệu và facet (ADR 0030)',
  })
  @Public()
  async searchCatalog(@Args('input') input: ProductSearchInput): Promise<ProductSearchResultModel> {
    const limit = Math.min(Math.max(input.limit, 1), MAX_PAGE_SIZE);
    const page = Math.max(input.page, 1);
    const res = await this.svc.search.search({
      ...(input.text?.trim() ? { text: input.text.trim().slice(0, 200) } : {}),
      ...(input.categoryId ? { categoryId: input.categoryId } : {}),
      ...(input.brandIds?.length ? { brandIds: input.brandIds } : {}),
      attributes: filters(input.attributes),
      options: filters(input.options),
      ...(input.currency ? { currency: input.currency } : {}),
      ...(input.minPrice != null ? { minPrice: input.minPrice } : {}),
      ...(input.maxPrice != null ? { maxPrice: input.maxPrice } : {}),
      ...(input.sort ? { sort: input.sort } : {}),
      page: page - 1,
      size: limit,
    });
    return {
      items: res.hits.map((h) => ({ id: h.id })),
      total: res.total,
      page,
      limit,
      categories: res.categories.map((c) => ({ value: c.categoryId, count: c.count })),
      brands: res.brands ?? [],
      attributes: res.attributes ?? [],
      options: res.options ?? [],
    };
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

  @ResolveField(() => Int) async discountPercent(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    const s = await this.summary(p, ctx);
    return discountPercent(s.price, s.originalPrice);
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

  @ResolveField(() => String, { nullable: true }) async description(
    @Parent() p: ProductSource,
    @Context() ctx: GqlCtx,
  ) {
    return (await this.detail(p, ctx)).description ?? null;
  }

  @ResolveField(() => String, { nullable: true }) async thumbnail(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).thumbnail ?? null;
  }

  @ResolveField(() => [String]) async images(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    const imgs = (await this.detail(p, ctx)).image ?? [];
    return [...imgs].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)).map((i) => i.imagePath);
  }

  @ResolveField(() => String, { nullable: true }) async brandName(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).brand ?? null;
  }

  @ResolveField(() => [CategoryRef]) async categories(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    const cats = (await this.detail(p, ctx)).category ?? [];
    return [...cats].sort((a, b) => (a.displayOrder ?? 0) - (b.displayOrder ?? 0)).map((c) => ({ id: c.categoryId }));
  }

  @ResolveField(() => StockModel, { nullable: true }) async stock(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    try {
      const level = await loadersOf(ctx, this.svc).stock.load(p.id);
      return level ? stock(level) : { status: 'OUT_OF_STOCK', available: 0 };
    } catch {
      return null;
    }
  }

  private async summary(p: ProductSource, ctx: GqlCtx): Promise<ProductSummary> {
    const s = await loadersOf(ctx, this.svc).summary.load(p.id);
    if (!s) throw DomainError.notFound(`product ${p.id} not found`);
    return s;
  }

  private detail(p: ProductSource, ctx: GqlCtx): Promise<ProductDetail> {
    return loadersOf(ctx, this.svc).detail.load(p.id);
  }

  private prime(ctx: GqlCtx, list: ProductDetail[]): ProductSource[] {
    const loaders = loadersOf(ctx, this.svc);
    for (const d of list) loaders.detail.prime(d.id, d);
    return list.map((d) => ({ id: d.id }));
  }
}

/** Gộp bộ lọc trùng key; bỏ bộ lọc rỗng. */
function filters(list: readonly FacetFilterInput[] | null | undefined): Record<string, string[]> {
  const out: Record<string, string[]> = {};
  for (const f of list ?? []) {
    const values = f.values.map((v) => v.trim()).filter(Boolean);
    if (!f.key.trim() || values.length === 0) continue;
    out[f.key.trim()] = [...(out[f.key.trim()] ?? []), ...values];
  }
  return out;
}
