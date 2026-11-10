import type { Database } from '@ecom/platform';
import { and, asc, count, eq, inArray, isNull, ne, sql } from 'drizzle-orm';
import type {
  BrandRepository,
  CategoryFilter,
  CategoryRepository,
  CountryRepository,
  Page,
  TagRepository,
} from '../../../application/ports.js';
import type { BrandProps } from '../../../domain/brand.js';
import { Category } from '../../../domain/category.js';
import type { Country } from '../../../domain/country.js';
import type { TagProps, TagStatus } from '../../../domain/tag.js';
import * as schema from './schema.js';
import { brands, categories, countries, productTags, tags } from './schema.js';

/** `db` hoặc `tx` của Drizzle với schema catalog. */
export type Conn = Pick<Database<typeof schema>, 'select' | 'insert' | 'update' | 'delete' | 'execute'>;

type CategoryRow = typeof categories.$inferSelect;
const toCategory = (r: CategoryRow) => Category.rehydrate({ ...r });

export class DrizzleCategoryRepository implements CategoryRepository {
  constructor(private readonly db: Conn) {}

  async findById(id: string) {
    const [row] = await this.db.select().from(categories).where(eq(categories.id, id));
    return row && toCategory(row);
  }

  async findByIds(ids: readonly string[]) {
    if (ids.length === 0) return [];
    const rows = await this.db
      .select()
      .from(categories)
      .where(inArray(categories.id, [...ids]));
    return rows.map(toCategory);
  }

  async findBySlug(slug: string) {
    const [row] = await this.db.select().from(categories).where(eq(categories.slug, slug));
    return row && toCategory(row);
  }

  async list(filter: CategoryFilter) {
    const conds = [];
    if (filter.parentId === null) conds.push(isNull(categories.parentId));
    else if (filter.parentId !== undefined) conds.push(eq(categories.parentId, filter.parentId));
    if (!filter.includeInactive) conds.push(eq(categories.status, 'ACTIVE'));
    const rows = await this.db
      .select()
      .from(categories)
      .where(and(...conds))
      .orderBy(asc(categories.sortOrder), asc(categories.name));
    return rows.map(toCategory);
  }

  async pathTo(id: string): Promise<string[]> {
    // CTE đệ quy đi ngược lên gốc; depth giới hạn để dữ liệu hỏng (vòng) không treo truy vấn.
    const result = await this.db.execute<{ id: string }>(sql`
      WITH RECURSIVE up AS (
        SELECT id, parent_id, 0 AS depth FROM ${categories} WHERE id = ${id}
        UNION ALL
        SELECT c.id, c.parent_id, up.depth + 1 FROM ${categories} c JOIN up ON c.id = up.parent_id WHERE up.depth < 10
      )
      SELECT id FROM up ORDER BY depth DESC`);
    return result.rows.map((r) => r.id);
  }

  async countChildren(id: string, activeOnly: boolean) {
    const conds = [eq(categories.parentId, id)];
    if (activeOnly) conds.push(eq(categories.status, 'ACTIVE'));
    const [row] = await this.db
      .select({ n: count() })
      .from(categories)
      .where(and(...conds));
    return row?.n ?? 0;
  }

  async slugTaken(slug: string, exceptId?: string) {
    const conds = [eq(categories.slug, slug)];
    if (exceptId) conds.push(ne(categories.id, exceptId));
    const [row] = await this.db
      .select({ n: count() })
      .from(categories)
      .where(and(...conds));
    return (row?.n ?? 0) > 0;
  }

  async insert(category: Category) {
    await this.db.insert(categories).values(category.snapshot());
  }

  async update(category: Category, expectedVersion: number) {
    const { id, createdAt: _c, version: _v, ...rest } = category.snapshot();
    const rows = await this.db
      .update(categories)
      .set({ ...rest, version: expectedVersion + 1 })
      .where(and(eq(categories.id, id), eq(categories.version, expectedVersion)))
      .returning({ id: categories.id });
    return rows.length === 1;
  }
}

export class DrizzleBrandRepository implements BrandRepository {
  constructor(private readonly db: Conn) {}

  list(includeInactive: boolean): Promise<BrandProps[]> {
    return this.db
      .select()
      .from(brands)
      .where(includeInactive ? undefined : eq(brands.active, true))
      .orderBy(asc(brands.name));
  }

  async findById(id: number) {
    const [row] = await this.db.select().from(brands).where(eq(brands.id, id));
    return row;
  }

  async findByIds(ids: readonly number[]) {
    if (ids.length === 0) return [];
    return this.db
      .select()
      .from(brands)
      .where(inArray(brands.id, [...ids]));
  }

  async slugTaken(slug: string, exceptId?: number) {
    const conds = [eq(brands.slug, slug)];
    if (exceptId !== undefined) conds.push(ne(brands.id, exceptId));
    const [row] = await this.db
      .select({ n: count() })
      .from(brands)
      .where(and(...conds));
    return (row?.n ?? 0) > 0;
  }

  async insert(brand: Omit<BrandProps, 'id'>) {
    const [row] = await this.db.insert(brands).values(brand).returning();
    if (!row) throw new Error('insert brand returned nothing');
    return row;
  }

  async update(brand: BrandProps) {
    const { id, ...rest } = brand;
    await this.db.update(brands).set(rest).where(eq(brands.id, id));
  }
}

export class DrizzleTagRepository implements TagRepository {
  constructor(private readonly db: Conn) {}

  async page(offset: number, limit: number, status?: TagStatus): Promise<Page<TagProps>> {
    const where = status ? eq(tags.status, status) : undefined;
    const [items, totals] = await Promise.all([
      this.db.select().from(tags).where(where).orderBy(asc(tags.name)).limit(limit).offset(offset),
      this.db.select({ n: count() }).from(tags).where(where),
    ]);
    return { items, total: totals[0]?.n ?? 0 };
  }

  async findById(id: number) {
    const [row] = await this.db.select().from(tags).where(eq(tags.id, id));
    return row;
  }

  async findByIds(ids: readonly number[]) {
    if (ids.length === 0) return [];
    return this.db
      .select()
      .from(tags)
      .where(inArray(tags.id, [...ids]));
  }

  async findByName(name: string) {
    const [row] = await this.db.select().from(tags).where(eq(tags.name, name));
    return row;
  }

  async insert(name: string, now: Date) {
    const [row] = await this.db.insert(tags).values({ name, createdAt: now, updatedAt: now }).returning();
    if (!row) throw new Error('insert tag returned nothing');
    return row;
  }

  async update(tag: TagProps) {
    const { id, createdAt: _c, ...rest } = tag;
    await this.db.update(tags).set(rest).where(eq(tags.id, id));
  }

  async tagIdsOfProducts(productIds: readonly string[]) {
    const out = new Map<string, number[]>(productIds.map((id) => [id, []]));
    if (productIds.length === 0) return out;
    const rows = await this.db
      .select()
      .from(productTags)
      .where(inArray(productTags.productId, [...productIds]));
    for (const r of rows) out.get(r.productId)?.push(r.tagId);
    return out;
  }

  async setProductTags(productId: string, tagIds: readonly number[]) {
    await this.db.delete(productTags).where(eq(productTags.productId, productId));
    if (tagIds.length > 0) {
      await this.db.insert(productTags).values(tagIds.map((tagId) => ({ productId, tagId })));
    }
  }
}

export class DrizzleCountryRepository implements CountryRepository {
  constructor(private readonly db: Conn) {}

  list(): Promise<Country[]> {
    return this.db.select().from(countries).orderBy(asc(countries.name));
  }

  async findByIso(isoCode: string) {
    const [row] = await this.db
      .select()
      .from(countries)
      .where(eq(sql`upper(${countries.isoCode})`, isoCode.toUpperCase()));
    return row;
  }
}
