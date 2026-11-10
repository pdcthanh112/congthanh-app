import { gql } from '@/graphql/client';
import { CategoriesQuery } from '@/graphql/documents';
import { ok, toCategory } from '@/graphql/legacy';
import { Category, Response } from '@/models/types';

/** Danh mục gốc (catalog-service qua supergraph). Tham số phân trang giữ cho tương thích, không còn dùng. */
export const getAllCategory = async (_page?: number, _limit?: number): Promise<Response<Category[]>> => {
  const { categories } = await gql(CategoriesQuery, { parentId: null });
  return ok(
    categories.map((c) => ({
      id: c.id,
      name: c.name,
      slug: c.slug,
      description: c.description ?? '',
      parentId: c.parentId ?? '',
    }))
  );
};

/** Cây danh mục (gốc + một cấp con) cho menu và bộ lọc. */
export const getAllCategoryJson = async (): Promise<Response<Category[]>> => {
  const { categories } = await gql(CategoriesQuery, { parentId: null });
  return ok(categories.map(toCategory));
};
