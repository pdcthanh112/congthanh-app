import axiosInstance from '@/config/axiosConfig';
import { gql } from '@/graphql/client';
import { RecentSearchesQuery, RecordSearchMutation, RemoveSearchMutation } from '@/graphql/documents';
import { ok } from '@/graphql/legacy';

// Lịch sử tìm kiếm: customer-service qua supergraph. Gợi ý: search-service (Elasticsearch) qua REST.
export const getSearchHistory = async () => {
  const { me } = await gql(RecentSearchesQuery, { limit: 10 });
  return ok(me.recentSearches);
};

export const addSearchHistoryItem = async (keyword: string) => ok((await gql(RecordSearchMutation, { keyword })).recordSearch);

/** Lịch sử định danh theo từ khoá. */
export const removeSearchHistoryItem = async (keyword: string) =>
  ok((await gql(RemoveSearchMutation, { keyword })).removeSearch);

export const getSearchRecommend = async (keyword: string): Promise<string[]> => {
  if (!keyword.trim()) return [];
  const res = await axiosInstance.get('search/suggest', { params: { q: keyword, limit: 8 } });
  const data = res.data?.data as { text?: string; name?: string }[] | string[] | undefined;
  return (data ?? []).map((s) => (typeof s === 'string' ? s : (s.text ?? s.name ?? ''))).filter(Boolean);
};
