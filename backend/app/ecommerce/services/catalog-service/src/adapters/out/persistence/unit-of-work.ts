import { create } from '@bufbuild/protobuf';
import { timestampFromDate } from '@bufbuild/protobuf/wkt';
import { type Database, DB, OutboxWriter } from '@ecommerce/platform';
import {
  BrandUpsertedSchema,
  CategoryUpsertedSchema,
  ProductTagsChangedSchema,
} from '@ecommerce/proto/ecommerce/catalog/v1/catalog_events_pb';
import { Inject, Injectable } from '@nestjs/common';
import type { CatalogEvents, ReadRepositories, TransactionScope, UnitOfWork } from '../../../application/ports.js';
import type { BrandProps } from '../../../domain/brand.js';
import type { Category } from '../../../domain/category.js';
import {
  type Conn,
  DrizzleBrandRepository,
  DrizzleCategoryRepository,
  DrizzleCountryRepository,
  DrizzleTagRepository,
} from './repositories.js';
import type * as schema from './schema.js';
import { outbox } from './schema.js';

const AGGREGATE = 'catalog';

class OutboxCatalogEvents implements CatalogEvents {
  constructor(
    private readonly tx: Conn,
    private readonly writer: OutboxWriter,
  ) {}

  async categoryChanged(category: Category, path: readonly string[]) {
    const c = category.snapshot();
    await this.writer.write(this.tx, {
      aggregateType: AGGREGATE,
      aggregateId: `category:${c.id}`,
      schema: CategoryUpsertedSchema,
      payload: create(CategoryUpsertedSchema, {
        categoryId: c.id,
        name: c.name,
        slug: c.slug,
        parentId: c.parentId ?? '',
        status: c.status,
        path: [...path],
        updatedAt: timestampFromDate(c.updatedAt),
      }),
    });
  }

  async brandChanged(brand: BrandProps) {
    await this.writer.write(this.tx, {
      aggregateType: AGGREGATE,
      aggregateId: `brand:${brand.id}`,
      schema: BrandUpsertedSchema,
      payload: create(BrandUpsertedSchema, {
        brandId: BigInt(brand.id),
        name: brand.name,
        slug: brand.slug,
        image: brand.image ?? '',
      }),
    });
  }

  async productTagsChanged(productId: string, tagIds: readonly number[]) {
    await this.writer.write(this.tx, {
      aggregateType: AGGREGATE,
      aggregateId: `product:${productId}`,
      schema: ProductTagsChangedSchema,
      payload: create(ProductTagsChangedSchema, { productId, tagIds: tagIds.map(BigInt) }),
    });
  }
}

/** Mỗi use case ghi chạy trong một transaction Postgres; repository + outbox dùng chung `tx`. */
@Injectable()
export class DrizzleUnitOfWork implements UnitOfWork {
  private readonly writer: OutboxWriter;

  constructor(@Inject(DB) private readonly db: Database<typeof schema>) {
    this.writer = new OutboxWriter('catalog-service', outbox);
  }

  run<T>(work: (scope: TransactionScope) => Promise<T>): Promise<T> {
    return this.db.transaction((tx) =>
      work({
        categories: new DrizzleCategoryRepository(tx),
        brands: new DrizzleBrandRepository(tx),
        tags: new DrizzleTagRepository(tx),
        events: new OutboxCatalogEvents(tx, this.writer),
      }),
    );
  }
}

export function readRepositories(db: Database<typeof schema>): ReadRepositories {
  return {
    categories: new DrizzleCategoryRepository(db),
    brands: new DrizzleBrandRepository(db),
    tags: new DrizzleTagRepository(db),
    countries: new DrizzleCountryRepository(db),
  };
}
