import type { Message } from './notification.js';

export interface MoneyLike {
  currencyCode: string;
  units: bigint;
  nanos: number;
}

export function formatMoney(m: MoneyLike | undefined): string {
  if (!m) return '';
  const value = Number(m.units) + m.nanos / 1e9;
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: m.currencyCode || 'VND' }).format(value);
}

const shortId = (id: string) => id.slice(-8).toUpperCase();

/** Nội dung tiếng Việt cho từng loại thông báo (giữ ngắn để vừa push/SMS). */
export const templates = {
  orderPlaced: (orderId: string, total: string): Message => ({
    title: 'Đặt hàng thành công',
    body: `Đơn #${shortId(orderId)} (${total}) đã được tạo. Hoàn tất thanh toán để chúng tôi xử lý đơn.`,
    link: `/orders/${orderId}`,
  }),
  orderConfirmed: (orderId: string): Message => ({
    title: 'Thanh toán thành công',
    body: `Đơn #${shortId(orderId)} đã thanh toán và đang được chuẩn bị.`,
    link: `/orders/${orderId}`,
  }),
  orderCancelled: (orderId: string, reason: string): Message => ({
    title: 'Đơn hàng đã huỷ',
    body: `Đơn #${shortId(orderId)} đã bị huỷ${reason ? `: ${reason}` : ''}.`,
    link: `/orders/${orderId}`,
  }),
  paymentFailed: (orderId: string, reason: string): Message => ({
    title: 'Thanh toán không thành công',
    body: `Thanh toán đơn #${shortId(orderId)} thất bại${reason ? ` (${reason})` : ''}. Vui lòng thử lại.`,
    link: `/orders/${orderId}/pay`,
  }),
  paymentRefunded: (orderId: string, amount: string): Message => ({
    title: 'Đã hoàn tiền',
    body: `Đã hoàn ${amount} cho đơn #${shortId(orderId)}.`,
    link: `/orders/${orderId}`,
  }),
  reviewReplied: (productId: string): Message => ({
    title: 'Người bán đã trả lời đánh giá của bạn',
    body: 'Xem phản hồi của người bán cho đánh giá sản phẩm.',
    link: `/products/${productId}#reviews`,
  }),
  reviewHidden: (productId: string, reason: string): Message => ({
    title: 'Đánh giá của bạn đã bị ẩn',
    body: `Lý do: ${reason}`,
    link: `/products/${productId}#reviews`,
  }),
  priceDrop: (productId: string, name: string, oldPrice: string, newPrice: string): Message => ({
    title: 'Sản phẩm yêu thích đang giảm giá',
    body: `${name}: ${oldPrice} → ${newPrice}`,
    link: `/products/${productId}`,
  }),
  backInStock: (productId: string, name: string): Message => ({
    title: 'Sản phẩm yêu thích đã có hàng lại',
    body: `${name} đã có hàng. Đặt ngay trước khi hết!`,
    link: `/products/${productId}`,
  }),
  newMessage: (conversationId: string, preview: string): Message => ({
    title: 'Tin nhắn mới',
    body: preview,
    link: `/messages/${conversationId}`,
  }),
  lowStock: (productId: string, available: number): Message => ({
    title: 'Sắp hết hàng',
    body: `Sản phẩm ${productId} chỉ còn ${available}.`,
    link: `/backoffice/inventory?product=${productId}`,
  }),
};

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** Email HTML tối giản, an toàn (escape nội dung). */
export function emailHtml(m: Message, appUrl: string): string {
  const link = m.link ? `${appUrl}${m.link}` : appUrl;
  return `<!doctype html><html lang="vi"><body style="font-family:system-ui,sans-serif;max-width:560px;margin:auto;padding:24px">
<h2 style="margin:0 0 12px">${esc(m.title)}</h2><p style="line-height:1.5">${esc(m.body)}</p>
<p><a href="${esc(link)}" style="display:inline-block;padding:10px 16px;background:#111;color:#fff;border-radius:6px;text-decoration:none">Xem chi tiết</a></p>
<p style="color:#888;font-size:12px">Bạn nhận email này vì có tài khoản tại CongThanh. Đổi tuỳ chọn thông báo trong phần Tài khoản.</p>
</body></html>`;
}
