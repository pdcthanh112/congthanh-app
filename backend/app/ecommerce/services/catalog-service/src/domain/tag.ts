import { DomainError } from '@ecommerce/platform/errors';

export type TagStatus = 'ACTIVE' | 'INACTIVE';

export interface TagProps {
  id: number;
  name: string;
  status: TagStatus;
  createdAt: Date;
  updatedAt: Date;
}

export const MAX_TAGS_PER_PRODUCT = 20;

export function normalizeTagName(name: string): string {
  const n = name?.trim().toLowerCase().replace(/\s+/g, ' ');
  if (!n) throw DomainError.invalid('tag name is required');
  if (n.length > 50) throw DomainError.invalid('tag name is at most 50 characters');
  return n;
}
