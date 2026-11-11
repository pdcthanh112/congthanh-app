import type { Message } from './notification.js';

export interface MoneyLike {
  currencyCode: string;
  units: bigint;
  nanos: number;
}

export function formatMoney(m: MoneyLike | undefined): string {
  if (!m) return '';
  const value = Number(m.units) + m.nanos / 1e9;
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: m.currencyCode || 'USD' }).format(value);
}

const shortId = (id: string) => id.slice(-8).toUpperCase();

/** Nội dung tiếng Anh (en-US) cho từng loại thông báo (giữ ngắn để vừa push/SMS). */
export const templates = {
  orderPlaced: (orderId: string, total: string): Message => ({
    title: 'Order placed',
    body: `Order #${shortId(orderId)} (${total}) has been created. Complete your payment so we can process it.`,
    link: `/orders/${orderId}`,
  }),
  orderConfirmed: (orderId: string): Message => ({
    title: 'Payment received',
    body: `Order #${shortId(orderId)} is paid and being prepared.`,
    link: `/orders/${orderId}`,
  }),
  orderCancelled: (orderId: string, reason: string): Message => ({
    title: 'Order cancelled',
    body: `Order #${shortId(orderId)} was cancelled${reason ? `: ${reason}` : ''}.`,
    link: `/orders/${orderId}`,
  }),
  paymentFailed: (orderId: string, reason: string): Message => ({
    title: 'Payment failed',
    body: `Payment for order #${shortId(orderId)} failed${reason ? ` (${reason})` : ''}. Please try again.`,
    link: `/orders/${orderId}/pay`,
  }),
  paymentRefunded: (orderId: string, amount: string): Message => ({
    title: 'Refund issued',
    body: `We refunded ${amount} for order #${shortId(orderId)}.`,
    link: `/orders/${orderId}`,
  }),
  reviewReplied: (productId: string): Message => ({
    title: 'The seller replied to your review',
    body: "See the seller's response to your product review.",
    link: `/products/${productId}#reviews`,
  }),
  reviewHidden: (productId: string, reason: string): Message => ({
    title: 'Your review was hidden',
    body: `Reason: ${reason}`,
    link: `/products/${productId}#reviews`,
  }),
  priceDrop: (productId: string, name: string, oldPrice: string, newPrice: string): Message => ({
    title: 'A wishlist item is on sale',
    body: `${name}: ${oldPrice} → ${newPrice}`,
    link: `/products/${productId}`,
  }),
  backInStock: (productId: string, name: string): Message => ({
    title: 'A wishlist item is back in stock',
    body: `${name} is available again. Order before it sells out!`,
    link: `/products/${productId}`,
  }),
  newMessage: (conversationId: string, preview: string): Message => ({
    title: 'New message',
    body: preview,
    link: `/messages/${conversationId}`,
  }),
  lowStock: (productId: string, available: number): Message => ({
    title: 'Low stock',
    body: `Product ${productId} has only ${available} left.`,
    link: `/backoffice/inventory?product=${productId}`,
  }),
};

const esc = (s: string) => s.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** Email HTML tối giản, an toàn (escape nội dung). */
export function emailHtml(m: Message, appUrl: string): string {
  const link = m.link ? `${appUrl}${m.link}` : appUrl;
  return `<!doctype html><html lang="en"><body style="font-family:system-ui,sans-serif;max-width:560px;margin:auto;padding:24px">
<h2 style="margin:0 0 12px">${esc(m.title)}</h2><p style="line-height:1.5">${esc(m.body)}</p>
<p><a href="${esc(link)}" style="display:inline-block;padding:10px 16px;background:#111;color:#fff;border-radius:6px;text-decoration:none">View details</a></p>
<p style="color:#888;font-size:12px">You're receiving this email because you have a CongThanh account. Manage notification preferences under Account.</p>
</body></html>`;
}
