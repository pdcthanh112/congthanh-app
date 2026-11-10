import { CurrentPrincipal, DomainError, hasAnyRole, type Principal, Public, Role, Roles } from '@ecom/platform';
import { Inject } from '@nestjs/common';
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
import { CatalogService } from '../../../application/catalog.service.js';
import { PRODUCT_DIRECTORY, type ProductDirectory } from '../../../application/ports.js';
import { type Loaders, loadersFor, toBrandModel, toCategoryModel, toTagModel } from './mappers.js';
import {
  BrandInput,
  BrandModel,
  CategoryModel,
  CountryModel,
  CreateCategoryInput,
  MoveCategoryInput,
  ProductRef,
  TagModel,
  TagPageModel,
  TagStatusGql,
  UpdateCategoryInput,
} from './types.js';

type Ctx = { loaders?: Loaders };

@Resolver(() => CategoryModel)
export class CategoryResolver {
  constructor(private readonly catalog: CatalogService) {}

  @Query(() => [CategoryModel], { description: 'Danh mục con của parentId (bỏ trống = danh mục gốc)' })
  @Public()
  async categories(
    @Args('parentId', { type: () => ID, nullable: true }) parentId: string | null,
    @Args('includeInactive', { defaultValue: false }) includeInactive: boolean,
    @CurrentPrincipal() principal?: Principal,
  ): Promise<CategoryModel[]> {
    const showInactive = includeInactive && hasAnyRole(principal, [Role.ADMIN]);
    const rows = await this.catalog.read.categories.list({ parentId: parentId ?? null, includeInactive: showInactive });
    return rows.map(toCategoryModel);
  }

  @Query(() => CategoryModel, { nullable: true })
  @Public()
  async category(
    @Args('id', { type: () => ID, nullable: true }) id?: string,
    @Args('slug', { nullable: true }) slug?: string,
  ): Promise<CategoryModel | null> {
    if (!id && !slug) throw DomainError.invalid('id or slug is required');
    const c = id
      ? await this.catalog.read.categories.findById(id)
      : await this.catalog.read.categories.findBySlug(slug!);
    return c ? toCategoryModel(c) : null;
  }

  @ResolveField(() => CategoryModel, { nullable: true })
  async parent(@Parent() c: CategoryModel, @Context() ctx: Ctx): Promise<CategoryModel | null> {
    if (!c.parentId) return null;
    const p = await loadersFor(ctx, this.catalog.read).category.load(c.parentId);
    return p ? toCategoryModel(p) : null;
  }

  @ResolveField(() => [CategoryModel])
  async children(@Parent() c: CategoryModel, @Context() ctx: Ctx): Promise<CategoryModel[]> {
    return (await loadersFor(ctx, this.catalog.read).children.load(c.id)).map(toCategoryModel);
  }

  @ResolveField(() => [CategoryModel], { description: 'Breadcrumb từ gốc tới danh mục này' })
  async path(@Parent() c: CategoryModel, @Context() ctx: Ctx): Promise<CategoryModel[]> {
    const ids = await this.catalog.read.categories.pathTo(c.id);
    const rows = await loadersFor(ctx, this.catalog.read).category.loadMany(ids);
    return rows.filter((r) => r && !(r instanceof Error)).map((r) => toCategoryModel(r as never));
  }

  @ResolveReference()
  async resolveReference(ref: { id: string }, @Context() ctx: Ctx): Promise<CategoryModel | null> {
    const c = await loadersFor(ctx, this.catalog.read).category.load(ref.id);
    return c ? toCategoryModel(c) : null;
  }

  @Mutation(() => CategoryModel)
  @Roles(Role.ADMIN)
  async createCategory(@Args('input') input: CreateCategoryInput): Promise<CategoryModel> {
    return toCategoryModel(await this.catalog.createCategory(input));
  }

  @Mutation(() => CategoryModel)
  @Roles(Role.ADMIN)
  async updateCategory(@Args('input') input: UpdateCategoryInput): Promise<CategoryModel> {
    const { id, version, ...draft } = input;
    return toCategoryModel(await this.catalog.updateCategory(id, version, draft));
  }

  @Mutation(() => CategoryModel)
  @Roles(Role.ADMIN)
  async moveCategory(@Args('input') input: MoveCategoryInput): Promise<CategoryModel> {
    return toCategoryModel(await this.catalog.moveCategory(input.id, input.version, input.parentId ?? null));
  }

  @Mutation(() => CategoryModel)
  @Roles(Role.ADMIN)
  async setCategoryActive(
    @Args('id', { type: () => ID }) id: string,
    @Args('version', { type: () => Int }) version: number,
    @Args('active') active: boolean,
  ): Promise<CategoryModel> {
    const c = active
      ? await this.catalog.activateCategory(id, version)
      : await this.catalog.deactivateCategory(id, version);
    return toCategoryModel(c);
  }
}

@Resolver(() => BrandModel)
export class BrandResolver {
  constructor(private readonly catalog: CatalogService) {}

  @Query(() => [BrandModel])
  @Public()
  async brands(
    @Args('includeInactive', { defaultValue: false }) includeInactive: boolean,
    @CurrentPrincipal() p?: Principal,
  ) {
    return (await this.catalog.read.brands.list(includeInactive && hasAnyRole(p, [Role.ADMIN]))).map(toBrandModel);
  }

  @Query(() => BrandModel, { nullable: true })
  @Public()
  async brand(@Args('id', { type: () => Int }) id: number, @Context() ctx: Ctx) {
    const b = await loadersFor(ctx, this.catalog.read).brand.load(id);
    return b ? toBrandModel(b) : null;
  }

  @ResolveReference()
  async resolveReference(ref: { id: number }, @Context() ctx: Ctx) {
    const b = await loadersFor(ctx, this.catalog.read).brand.load(Number(ref.id));
    return b ? toBrandModel(b) : null;
  }

  @Mutation(() => BrandModel)
  @Roles(Role.ADMIN)
  async createBrand(@Args('input') input: BrandInput) {
    return toBrandModel(await this.catalog.createBrand(input));
  }

  @Mutation(() => BrandModel)
  @Roles(Role.ADMIN)
  async updateBrand(@Args('id', { type: () => Int }) id: number, @Args('input') input: BrandInput) {
    return toBrandModel(await this.catalog.updateBrand(id, input));
  }
}

@Resolver(() => TagModel)
export class TagResolver {
  constructor(
    private readonly catalog: CatalogService,
    @Inject(PRODUCT_DIRECTORY) private readonly products: ProductDirectory,
  ) {}

  @Query(() => TagPageModel)
  @Public()
  async tags(
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 20 }) size: number,
    @Args('status', { type: () => TagStatusGql, nullable: true }) status?: TagStatusGql,
  ): Promise<TagPageModel> {
    const result = await this.catalog.tags(page, size, status);
    return { items: result.items.map(toTagModel), total: result.total, page, size: Math.min(Math.max(size, 1), 100) };
  }

  @Mutation(() => TagModel, { description: 'Tạo tag (trùng tên thì trả tag đã có)' })
  @Roles(Role.ADMIN, Role.SELLER)
  async createTag(@Args('name') name: string) {
    return toTagModel(await this.catalog.createTag(name));
  }

  @Mutation(() => TagModel)
  @Roles(Role.ADMIN)
  async updateTag(
    @Args('id', { type: () => Int }) id: number,
    @Args('name', { nullable: true }) name?: string,
    @Args('status', { type: () => TagStatusGql, nullable: true }) status?: TagStatusGql,
  ) {
    return toTagModel(await this.catalog.updateTag(id, name, status));
  }

  @Mutation(() => [TagModel], { description: 'Gán toàn bộ tag cho sản phẩm; seller chỉ sửa sản phẩm của mình' })
  @Roles(Role.ADMIN, Role.SELLER)
  async setProductTags(
    @Args('productId', { type: () => ID }) productId: string,
    @Args('tagIds', { type: () => [Int] }) tagIds: number[],
    @CurrentPrincipal() principal: Principal,
  ) {
    if (!hasAnyRole(principal, [Role.ADMIN])) {
      const seller = await this.products.sellerOf(productId);
      if (!seller) throw DomainError.notFound(`product ${productId} not found`);
      if (seller !== principal.id) throw DomainError.forbidden('you can only tag your own products');
    }
    const ids = await this.catalog.setProductTags(productId, tagIds);
    return (await this.catalog.read.tags.findByIds(ids)).map(toTagModel);
  }
}

@Resolver(() => ProductRef)
export class ProductTagsResolver {
  constructor(private readonly catalog: CatalogService) {}

  @ResolveReference()
  resolveReference(ref: { id: string }): ProductRef {
    return { id: ref.id };
  }

  @ResolveField(() => [TagModel])
  async tags(@Parent() product: ProductRef, @Context() ctx: Ctx) {
    return (await loadersFor(ctx, this.catalog.read).productTags.load(product.id)).map(toTagModel);
  }
}

@Resolver(() => CountryModel)
export class CountryResolver {
  constructor(private readonly catalog: CatalogService) {}

  @Query(() => [CountryModel])
  @Public()
  countries() {
    return this.catalog.read.countries.list();
  }

  @Query(() => CountryModel, { nullable: true })
  @Public()
  async country(@Args('isoCode') isoCode: string) {
    return (await this.catalog.read.countries.findByIso(isoCode)) ?? null;
  }
}
