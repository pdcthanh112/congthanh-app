import DataLoader from 'dataloader';
import type { ReadRepositories } from '../../../application/ports.js';
import type { BrandProps } from '../../../domain/brand.js';
import type { Category } from '../../../domain/category.js';
import type { TagProps } from '../../../domain/tag.js';
import { type BrandModel, type CategoryModel, CategoryStatusGql, type TagModel, TagStatusGql } from './types.js';

export const toCategoryModel = (c: Category): CategoryModel => {
  const s = c.snapshot();
  return { ...s, status: s.status === 'ACTIVE' ? CategoryStatusGql.ACTIVE : CategoryStatusGql.INACTIVE };
};
export const toBrandModel = (b: BrandProps): BrandModel => ({ ...b });
export const toTagModel = (t: TagProps): TagModel => ({
  id: t.id,
  name: t.name,
  status: t.status === 'ACTIVE' ? TagStatusGql.ACTIVE : TagStatusGql.INACTIVE,
});

/** DataLoader theo request: gom N lời gọi `parent`/`children`/`tags` thành 1 truy vấn. */
export interface Loaders {
  category: DataLoader<string, Category | undefined>;
  children: DataLoader<string, Category[]>;
  brand: DataLoader<number, BrandProps | undefined>;
  productTags: DataLoader<string, TagProps[]>;
}

export function loadersFor(ctx: { loaders?: Loaders }, read: ReadRepositories): Loaders {
  ctx.loaders ??= {
    category: new DataLoader(async (ids) => {
      const rows = await read.categories.findByIds(ids);
      const byId = new Map(rows.map((c) => [c.id, c]));
      return ids.map((id) => byId.get(id));
    }),
    children: new DataLoader(async (parentIds) => {
      const all = await Promise.all(parentIds.map((id) => read.categories.list({ parentId: id })));
      return all;
    }),
    brand: new DataLoader(async (ids) => {
      const rows = await read.brands.findByIds(ids);
      const byId = new Map(rows.map((b) => [b.id, b]));
      return ids.map((id) => byId.get(id));
    }),
    productTags: new DataLoader(async (productIds) => {
      const map = await read.tags.tagIdsOfProducts(productIds);
      const tagIds = [...new Set([...map.values()].flat())];
      const tags = new Map((await read.tags.findByIds(tagIds)).map((t) => [t.id, t]));
      return productIds.map((pid) =>
        (map.get(pid) ?? []).map((id) => tags.get(id)).filter((t): t is TagProps => t?.status === 'ACTIVE'),
      );
    }),
  };
  return ctx.loaders;
}
