import { create } from '@bufbuild/protobuf';
import { timestampFromDate } from '@bufbuild/protobuf/wkt';
import type { ConnectRouter } from '@connectrpc/connect';
import { DomainError } from '@ecom/platform';
import { BrandService } from '@ecom/proto/ecommerce/catalog/v1/brand_service_pb';
import {
  type CategoryResponse,
  CategoryResponseSchema,
  CategoryService,
} from '@ecom/proto/ecommerce/catalog/v1/category_service_pb';
import { TagService } from '@ecom/proto/ecommerce/catalog/v1/tag_service_pb';
import type { CatalogService } from '../../../application/catalog.service.js';
import type { Category } from '../../../domain/category.js';

function toResponse(c: Category): CategoryResponse {
  const s = c.snapshot();
  return create(CategoryResponseSchema, {
    id: s.id,
    name: s.name,
    slug: s.slug,
    description: s.description ?? '',
    image: s.image ?? '',
    parentId: s.parentId ?? '',
    status: s.status,
    createdAt: timestampFromDate(s.createdAt),
    updatedAt: timestampFromDate(s.updatedAt),
  });
}

/** gRPC cho service nội bộ (product-service dùng khi gán danh mục, hiển thị tên/slug). */
export function catalogGrpcRoutes(catalog: CatalogService) {
  return (router: ConnectRouter) => {
    router.service(CategoryService, {
      async getCategoryById(req) {
        const c = await catalog.read.categories.findById(req.categoryId);
        if (!c) throw DomainError.notFound(`category ${req.categoryId} not found`);
        return toResponse(c);
      },
      async getCategoryBySlug(req) {
        const c = await catalog.read.categories.findBySlug(req.slug);
        if (!c) throw DomainError.notFound(`category ${req.slug} not found`);
        return toResponse(c);
      },
      async getListCategoryByIds(req) {
        const rows = await catalog.read.categories.findByIds(req.categoryId);
        return { response: rows.map(toResponse) };
      },
      async validateCategories(req) {
        const invalid = await catalog.validateCategories(req.categoryIds);
        return { valid: invalid.length === 0, invalidCategoryIds: invalid };
      },
    });
    router.service(BrandService, {
      async getBrandById(req) {
        const id = Number(req.brandId);
        const b = Number.isInteger(id) ? await catalog.read.brands.findById(id) : undefined;
        if (!b) throw DomainError.notFound(`brand ${req.brandId} not found`);
        return { id: b.id, name: b.name };
      },
    });
    router.service(TagService, {
      async getTagById(req) {
        const t = await catalog.read.tags.findById(Number(req.tagId));
        if (!t) throw DomainError.notFound(`tag ${req.tagId} not found`);
        return {
          id: BigInt(t.id),
          name: t.name,
          status: t.status,
          createdAt: timestampFromDate(t.createdAt),
          updatedAt: timestampFromDate(t.updatedAt),
        };
      },
    });
  };
}
