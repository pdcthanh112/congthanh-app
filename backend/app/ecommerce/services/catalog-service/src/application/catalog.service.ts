import { DomainError } from '@ecom/platform/errors';
import { Inject, Injectable } from '@nestjs/common';
import { type BrandDraft, type BrandProps, normalizeBrand } from '../domain/brand.js';
import { Category, type CategoryDraft, checkDepth } from '../domain/category.js';
import { MAX_TAGS_PER_PRODUCT, normalizeTagName, type TagProps, type TagStatus } from '../domain/tag.js';
import {
  CLOCK,
  type Clock,
  ID_GENERATOR,
  type IdGenerator,
  type Page,
  READ_REPOSITORIES,
  type ReadRepositories,
  type TransactionScope,
  UNIT_OF_WORK,
  type UnitOfWork,
} from './ports.js';

/** Use case ghi của catalog. Đọc đơn giản đi thẳng qua ReadRepositories (resolver, gRPC). */
@Injectable()
export class CatalogService {
  constructor(
    @Inject(UNIT_OF_WORK) private readonly uow: UnitOfWork,
    @Inject(READ_REPOSITORIES) readonly read: ReadRepositories,
    @Inject(CLOCK) private readonly clock: Clock,
    @Inject(ID_GENERATOR) private readonly ids: IdGenerator,
  ) {}

  // ---------- danh mục ----------

  createCategory(draft: CategoryDraft): Promise<Category> {
    return this.uow.run(async (tx) => {
      const now = this.clock.now();
      const category = Category.create(this.ids.next(), draft, now);
      const parentPath = draft.parentId ? await this.requireParentPath(tx, draft.parentId) : [];
      checkDepth(parentPath);
      await this.ensureSlugFree(tx, category.slug);
      await tx.categories.insert(category);
      await tx.events.categoryChanged(category, [...parentPath, category.id]);
      return category;
    });
  }

  updateCategory(id: string, version: number, draft: Omit<CategoryDraft, 'parentId'>): Promise<Category> {
    return this.mutateCategory(id, version, async (category, tx) => {
      category.rename(draft, this.clock.now());
      await this.ensureSlugFree(tx, category.slug, id);
    });
  }

  moveCategory(id: string, version: number, newParentId: string | null): Promise<Category> {
    return this.mutateCategory(id, version, async (category, tx) => {
      const parentPath = newParentId ? await this.requireParentPath(tx, newParentId) : [];
      category.moveTo(newParentId, parentPath, this.clock.now());
    });
  }

  /** Ẩn danh mục; chặn khi còn danh mục con đang hoạt động (phải ẩn hoặc chuyển con trước). */
  deactivateCategory(id: string, version: number): Promise<Category> {
    return this.mutateCategory(id, version, async (category, tx) => {
      if ((await tx.categories.countChildren(id, true)) > 0) {
        throw DomainError.conflict('category still has active subcategories', 'CATEGORY_HAS_CHILDREN');
      }
      category.deactivate(this.clock.now());
    });
  }

  activateCategory(id: string, version: number): Promise<Category> {
    return this.mutateCategory(id, version, async (category) => category.activate(this.clock.now()));
  }

  /** Kiểm tra danh sách id (product-service gọi khi gán danh mục cho sản phẩm). */
  async validateCategories(ids: readonly string[]): Promise<string[]> {
    const unique = [...new Set(ids)];
    const found = await this.read.categories.findByIds(unique);
    const active = new Set(found.filter((c) => c.status === 'ACTIVE').map((c) => c.id));
    return unique.filter((id) => !active.has(id));
  }

  // ---------- thương hiệu ----------

  createBrand(draft: BrandDraft): Promise<BrandProps> {
    return this.uow.run(async (tx) => {
      const data = normalizeBrand(draft);
      if (await tx.brands.slugTaken(data.slug))
        throw DomainError.conflict(`slug "${data.slug}" is taken`, 'SLUG_TAKEN');
      const brand = await tx.brands.insert({ ...data, active: true });
      await tx.events.brandChanged(brand);
      return brand;
    });
  }

  updateBrand(id: number, draft: BrandDraft & { active?: boolean | undefined }): Promise<BrandProps> {
    return this.uow.run(async (tx) => {
      const current = await tx.brands.findById(id);
      if (!current) throw DomainError.notFound(`brand ${id} not found`);
      const data = normalizeBrand(draft);
      if (await tx.brands.slugTaken(data.slug, id))
        throw DomainError.conflict(`slug "${data.slug}" is taken`, 'SLUG_TAKEN');
      const brand = { ...current, ...data, active: draft.active ?? current.active };
      await tx.brands.update(brand);
      await tx.events.brandChanged(brand);
      return brand;
    });
  }

  // ---------- tag ----------

  tags(page: number, size: number, status?: TagStatus): Promise<Page<TagProps>> {
    const limit = Math.min(Math.max(size, 1), 100);
    return this.read.tags.page(Math.max(page - 1, 0) * limit, limit, status);
  }

  createTag(name: string): Promise<TagProps> {
    return this.uow.run(async (tx) => {
      const normalized = normalizeTagName(name);
      const existing = await tx.tags.findByName(normalized);
      if (existing) return existing;
      return tx.tags.insert(normalized, this.clock.now());
    });
  }

  updateTag(id: number, name: string | undefined, status: TagStatus | undefined): Promise<TagProps> {
    return this.uow.run(async (tx) => {
      const tag = await tx.tags.findById(id);
      if (!tag) throw DomainError.notFound(`tag ${id} not found`);
      const next = {
        ...tag,
        name: name === undefined ? tag.name : normalizeTagName(name),
        status: status ?? tag.status,
        updatedAt: this.clock.now(),
      };
      if (next.name !== tag.name && (await tx.tags.findByName(next.name))) {
        throw DomainError.conflict(`tag "${next.name}" already exists`, 'TAG_EXISTS');
      }
      await tx.tags.update(next);
      return next;
    });
  }

  /** Gán toàn bộ tag cho sản phẩm (thay thế danh sách cũ). */
  setProductTags(productId: string, tagIds: readonly number[]): Promise<number[]> {
    return this.uow.run(async (tx) => {
      const unique = [...new Set(tagIds)];
      if (unique.length > MAX_TAGS_PER_PRODUCT) {
        throw DomainError.invalid(`a product can have at most ${MAX_TAGS_PER_PRODUCT} tags`);
      }
      const found = await tx.tags.findByIds(unique);
      const missing = unique.filter((id) => !found.some((t) => t.id === id && t.status === 'ACTIVE'));
      if (missing.length > 0) throw DomainError.invalid(`unknown or inactive tags: ${missing.join(', ')}`);
      await tx.tags.setProductTags(productId, unique);
      await tx.events.productTagsChanged(productId, unique);
      return unique;
    });
  }

  // ---------- nội bộ ----------

  private mutateCategory(
    id: string,
    version: number,
    change: (category: Category, tx: TransactionScope) => Promise<void>,
  ): Promise<Category> {
    return this.uow.run(async (tx) => {
      const category = await tx.categories.findById(id);
      if (!category) throw DomainError.notFound(`category ${id} not found`);
      if (category.snapshot().version !== version) {
        throw DomainError.conflict('category was modified by someone else; reload and retry', 'STALE_VERSION');
      }
      await change(category, tx);
      if (!(await tx.categories.update(category, version))) {
        throw DomainError.conflict('category was modified by someone else; reload and retry', 'STALE_VERSION');
      }
      const path = await tx.categories.pathTo(id);
      await tx.events.categoryChanged(category, path);
      return Category.rehydrate({ ...category.snapshot(), version: version + 1 });
    });
  }

  private async requireParentPath(tx: TransactionScope, parentId: string): Promise<string[]> {
    const path = await tx.categories.pathTo(parentId);
    if (path.length === 0) throw DomainError.invalid(`parent category ${parentId} not found`);
    return path;
  }

  private async ensureSlugFree(tx: TransactionScope, slug: string, exceptId?: string): Promise<void> {
    if (await tx.categories.slugTaken(slug, exceptId)) {
      throw DomainError.conflict(`slug "${slug}" is taken`, 'SLUG_TAKEN');
    }
  }
}
