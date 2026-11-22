import { DomainError } from "@ecommerce/platform/errors";

export const SEARCH_HISTORY_LIMIT = 30;

export interface SearchEntry {
  keyword: string;
  searchedAt: Date;
}

export function normalizeKeyword(keyword: string): string {
  const k = keyword?.trim().replace(/\s+/g, " ");
  if (!k) throw DomainError.invalid("keyword is required");
  return k.slice(0, 200);
}
