export const formatNumber = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    compactDisplay: 'short',
    maximumFractionDigits: 1,
  }).format(value);
};

export const formatMoney = (money: { amount: number; currency: string }, locale: string) =>
  new Intl.NumberFormat(locale, { style: 'currency', currency: money.currency }).format(money.amount);

export function getSupplierUrl(slug: string, path: string = '') {
  if (typeof window !== 'undefined') {
    const host = window.location.host;
    const protocol = window.location.protocol;
    return `${protocol}//${slug}.${host.split('.').slice(-2).join('.')}${path}`;
  }
  return `https://${slug}.mydomain.com${path}`;
}
