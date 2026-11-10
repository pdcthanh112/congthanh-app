import type { BrandProps } from '../domain/brand.js';
import type { Category } from '../domain/category.js';
import type { Country } from '../domain/country.js';
import type { TagProps, TagStatus } from '../domain/tag.js';

export interface CategoryFilter {
  /** undefined: mọi danh mục; null: chỉ gốc; string: con trực tiếp của id đó. */
  parentId?: string | null;
  includeInactive?: boolean;
}

export interface CategoryRepository {
  findById(id: string): Promise<Category | undefined>;
  findByIds(ids: readonly string[]): Promise<Category[]>;
  findBySlug(slug: string): Promise<Category | undefined>;
  list(filter: CategoryFilter): Promise<Category[]>;
  /** Id từ gốc tới `id` (gồm cả `id`); rỗng nếu không tồn tại. */
  pathTo(id: string): Promise<string[]>;
  countChildren(id: string, activeOnly: boolean): Promise<number>;
  slugTaken(slug: string, exceptId?: string): Promise<boolean>;
  insert(category: Category): Promise<void>;
  /** Cập nhật với khoá lạc quan; false nếu version đã đổi (có người sửa trước). */
  update(category: Category, expectedVersion: number): Promise<boolean>;
}

export interface BrandRepository {
  list(includeInactive: boolean): Promise<BrandProps[]>;
  findById(id: number): Promise<BrandProps | undefined>;
  findByIds(ids: readonly number[]): Promise<BrandProps[]>;
  slugTaken(slug: string, exceptId?: number): Promise<boolean>;
  insert(brand: Omit<BrandProps, 'id'>): Promise<BrandProps>;
  update(brand: BrandProps): Promise<void>;
}

export interface Page<T> {
  items: T[];
  total: number;
}

export interface TagRepository {
  page(offset: number, limit: number, status?: TagStatus): Promise<Page<TagProps>>;
  findById(id: number): Promise<TagProps | undefined>;
  findByIds(ids: readonly number[]): Promise<TagProps[]>;
  findByName(name: string): Promise<TagProps | undefined>;
  insert(name: string, now: Date): Promise<TagProps>;
  update(tag: TagProps): Promise<void>;
  tagIdsOfProducts(productIds: readonly string[]): Promise<Map<string, number[]>>;
  setProductTags(productId: string, tagIds: readonly number[]): Promise<void>;
}

export interface CountryRepository {
  list(): Promise<Country[]>;
  findByIso(isoCode: string): Promise<Country | undefined>;
}

/** Event ra ngoài qua outbox (ghi trong cùng transaction). */
export interface CatalogEvents {
  categoryChanged(category: Category, path: readonly string[]): Promise<void>;
  brandChanged(brand: BrandProps): Promise<void>;
  productTagsChanged(productId: string, tagIds: readonly number[]): Promise<void>;
}

export interface TransactionScope {
  categories: CategoryRepository;
  brands: BrandRepository;
  tags: TagRepository;
  events: CatalogEvents;
}

export interface UnitOfWork {
  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T>;
}

export interface ReadRepositories {
  categories: CategoryRepository;
  brands: BrandRepository;
  tags: TagRepository;
  countries: CountryRepository;
}

export interface Clock {
  now(): Date;
}
export interface IdGenerator {
  next(): string;
}

export const UNIT_OF_WORK = Symbol('UnitOfWork');
export const READ_REPOSITORIES = Symbol('ReadRepositories');
export const CLOCK = Symbol('Clock');
export const ID_GENERATOR = Symbol('IdGenerator');

/** Ai là người bán của sản phẩm (product-service), để seller chỉ gắn tag cho sản phẩm của mình. */
export interface ProductDirectory {
  sellerOf(productId: string): Promise<string | undefined>;
}
export const PRODUCT_DIRECTORY = Symbol('ProductDirectory');
