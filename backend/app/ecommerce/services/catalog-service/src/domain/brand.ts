import { DomainError } from '@ecom/platform/errors';
import { slugify } from './slug.js';

export interface BrandProps {
  id: number;
  name: string;
  slug: string;
  image: string | null;
  active: boolean;
}

export interface BrandDraft {
  name: string;
  slug?: string | null | undefined;
  image?: string | null | undefined;
}

/** Chuẩn hoá dữ liệu thương hiệu; id do Postgres cấp (bigint, product-service lưu dạng số). */
export function normalizeBrand(draft: BrandDraft): Omit<BrandProps, 'id' | 'active'> {
  const name = draft.name?.trim();
  if (!name) throw DomainError.invalid('brand name is required');
  const slug = slugify(draft.slug?.trim() || name);
  if (!slug) throw DomainError.invalid('brand slug is empty');
  return { name, slug, image: draft.image ?? null };
}
