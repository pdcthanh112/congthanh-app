import type { ProductDetail, ProductOptionValue, ProductSummary, StockLevel } from '@ecommerce/clients';
import { DomainError, Public } from '@ecommerce/platform';
import { Args, Context, ID, Int, Parent, Query, ResolveField, ResolveReference, Resolver } from '@nestjs/graphql';
import { StorefrontService } from '../../../application/storefront.service.js';
import { type GqlCtx, loadersOf } from './loaders.js';
import { decimalMoney, discountPercent, money, stock } from './mappers.js';
import {
  CategoryRef,
  type FacetFilterInput,
  MoneyModel,
  ProductAttributeModel,
  ProductModel,
  ProductOptionModel,
  ProductPageModel,
  ProductSearchInput,
  ProductSearchResultModel,
  type ProductSource,
  ProductSpecificationGroupModel,
  ProductVariantModel,
  StockModel,
  StockStatusGql,
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
    const productId = id ?? (await this.idBySlug(slug as string));
    if (!productId) return null;
    // Sản phẩm nháp hoặc ngừng bán không hiện ở storefront (giỏ hàng vẫn thấy qua CartLine.product để báo lỗi).
    const summary = await loadersOf(ctx, this.svc).summary.load(productId);
    return summary?.active ? { id: productId } : null;
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
    const res = categoryId ? await this.svc.products.byCategory(categoryId, p, size) : await this.svc.products.list(p, size);
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

  @ResolveField(() => String, { nullable: true }) async description(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
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

  @ResolveField(() => ID, { nullable: true }) async parentId(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).parentId ?? null;
  }

  @ResolveField(() => Boolean) async hasVariant(@Parent() p: ProductSource, @Context() ctx: GqlCtx) {
    return (await this.detail(p, ctx)).hasVariant ?? false;
  }

  /**
   * Thông số theo thứ tự product-service đã sắp (nhóm → thứ tự thuộc tính). Thuộc tính MULTI_SELECT có nhiều dòng
   * cùng thuộc tính: gộp thành một dòng, giá trị nối bằng ", " (ADR 0033).
   */
  @ResolveField(() => [ProductAttributeModel]) async attributes(@Parent() p: ProductSource, @Context() ctx: GqlCtx): Promise<ProductAttributeModel[]> {
    const byAttribute = new Map<string, { group: string | null; name: string; values: string[] }>();
    for (const a of (await this.detail(p, ctx)).attribute ?? []) {
      const value = attributeValue(a);
      if (!a.attribute || !value) continue;
      const key = a.attributeId == null ? a.attribute : String(a.attributeId);
      const row = byAttribute.get(key) ?? { group: a.groupName ?? null, name: a.attribute, values: [] };
      row.values.push(value);
      byAttribute.set(key, row);
    }
    return [...byAttribute.values()].map((r) => ({ group: r.group, name: r.name, value: r.values.join(', ') }));
  }

  @ResolveField(() => [ProductSpecificationGroupModel]) async specifications(@Parent() p: ProductSource, @Context() ctx: GqlCtx): Promise<ProductSpecificationGroupModel[]> {
    return ((await this.detail(p, ctx)).specifications ?? []).map((g) => ({ name: g.name ?? null, items: g.items }));
  }

  @ResolveField(() => [ProductOptionModel]) async options(@Parent() p: ProductSource, @Context() ctx: GqlCtx): Promise<ProductOptionModel[]> {
    if (!(await this.detail(p, ctx)).hasVariant) return [];
    const byOption = new Map<number, ProductOptionModel>();
    for (const v of await loadersOf(ctx, this.svc).optionValues.load(p.id)) {
      const option = byOption.get(v.productOptionId) ?? { id: String(v.productOptionId), name: v.productOptionName, values: [] };
      if (!option.values.includes(v.productOptionValue)) option.values.push(v.productOptionValue);
      byOption.set(v.productOptionId, option);
    }
    return [...byOption.values()];
  }

  @ResolveField(() => [ProductVariantModel]) async variants(@Parent() p: ProductSource, @Context() ctx: GqlCtx): Promise<ProductVariantModel[]> {
    if (!(await this.detail(p, ctx)).hasVariant) return [];
    const loaders = loadersOf(ctx, this.svc);
    const [variants, optionValues] = await Promise.all([loaders.variants.load(p.id), loaders.optionValues.load(p.id)]);
    const optionNames = optionNamesById(optionValues);
    // Tồn kho từng biến thể gom một lần gọi inventory-service; lỗi thì để null như Product.stock.
    const levels = await loaders.stock.loadMany(variants.map((v) => v.id));
    return variants.map((v, i) => {
      const level = levels[i];
      const images = (v.image ?? []).map((img) => img.imagePath);
      return {
        id: v.id,
        name: v.name,
        slug: v.slug ?? null,
        sku: v.sku ?? null,
        // Biến thể thiếu đơn vị tiền thì không đoán (ADR 0029): trả null, UI hiển thị giá của sản phẩm cha.
        price: v.price != null && v.currency ? decimalMoney({ amount: v.price, currency: v.currency }) : null,
        thumbnail: v.thumbnail?.imagePath ?? images[0] ?? null,
        images,
        options: Object.entries(v.options ?? {}).map(([optionId, value]) => ({
          optionId,
          name: optionNames.get(optionId) ?? '',
          value,
        })),
        stock: level instanceof Error ? null : variantStock(level),
      };
    });
  }

  private async idBySlug(slug: string): Promise<string | null> {
    try {
      return (await this.svc.products.bySlug(slug)).id;
    } catch (e) {
      if (e instanceof DomainError && e.kind === 'NOT_FOUND') return null;
      throw e;
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

/** Giá trị hiển thị của một dòng thông số: kèm đơn vị nếu thuộc tính có đơn vị. */
function attributeValue(a: { value?: string | null; valueNumber?: number | null; unit?: string | null }): string | null {
  const base = a.value ?? (a.valueNumber == null ? null : String(a.valueNumber));
  if (!base) return null;
  return a.unit ? `${base} ${a.unit}` : base;
}

/** Biến thể inventory-service không biết tới thì coi như hết hàng. */
function variantStock(level: StockLevel | null | undefined): StockModel {
  return level ? stock(level) : { status: StockStatusGql.OUT_OF_STOCK, available: 0 };
}

/** optionId (dạng chuỗi như key JSON của biến thể) → tên trục option. */
function optionNamesById(values: readonly ProductOptionValue[]): Map<string, string> {
  return new Map(values.map((v) => [String(v.productOptionId), v.productOptionName]));
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
