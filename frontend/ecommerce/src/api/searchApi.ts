import axiosInstance from '@/config/axiosConfig';
import { gql } from '@/graphql/client';
import { RecentSearchesQuery, RecordSearchMutation, RemoveSearchMutation } from '@/graphql/documents';
import { ok } from '@/graphql/legacy';

// Lịch sử tìm kiếm: customer-service qua supergraph. Gợi ý: search-service (OpenSearch) qua REST.
export const getSearchHistory = async () => {
  const { me } = await gql(RecentSearchesQuery, { limit: 10 });
  return ok(me.recentSearches);
};

export const addSearchHistoryItem = async (_customerId: string, value: string) =>
  ok((await gql(RecordSearchMutation, { keyword: value })).recordSearch);

/** Lịch sử giờ định danh theo từ khoá thay vì id. */
export const removeSearchHistoryItem = async (keyword: string | number) =>
  ok((await gql(RemoveSearchMutation, { keyword: String(keyword) })).removeSearch);

export const getSearchRecommend = async (keyword: string): Promise<string[]> => {
  if (!keyword.trim()) return [];
  const res = await axiosInstance.get('search/suggest', { params: { q: keyword, limit: 8 } });
  const data = res.data?.data as { text?: string; name?: string }[] | string[] | undefined;
  return (data ?? []).map((s) => (typeof s === 'string' ? s : (s.text ?? s.name ?? ''))).filter(Boolean);
};
