import { gql } from '@/graphql/client';
import { QuotePromotionQuery } from '@/graphql/documents';
import { ok } from '@/graphql/legacy';
import type { PromotionQuote } from '@/graphql/types';
import { Response, Voucher } from '@/models/types';

/**
 * Thử mã giảm giá trên giỏ hiện tại (promotion-service qua storefront-bff). Mã không hợp lệ thì ném lỗi với lý do.
 * Trả Voucher (model cũ) kèm quote để hiển thị số tiền giảm và tổng mới.
 */
export const getVoucherByCode = async (code: string): Promise<Response<Voucher & { quote: PromotionQuote }>> => {
  const { quotePromotion: q } = await gql(QuotePromotionQuery, { code: String(code) });
  if (!q.valid) throw new Error(q.reason ?? 'Invalid promotion code');
  return ok({
    id: code,
    code,
    type: 'Discount',
    value: q.discount?.amount ?? 0,
    usageLimit: 0,
    description: q.reason ?? '',
    startDate: new Date(),
    endDate: new Date(),
    status: 'ACTIVE',
    quote: q,
  });
};
