import { gql } from '@/graphql/client';
import { QuotePromotionQuery } from '@/graphql/documents';
import type { PromotionQuote } from '@/graphql/types';

/** Thử mã giảm giá trên giỏ hiện tại (promotion-service qua storefront-bff). Mã không hợp lệ thì ném lỗi kèm lý do. */
export const quotePromotion = async (code: string): Promise<PromotionQuote> => {
  const { quotePromotion: q } = await gql(QuotePromotionQuery, { code });
  if (!q.valid) throw new Error(q.reason ?? 'Invalid promotion code');
  return q;
};
