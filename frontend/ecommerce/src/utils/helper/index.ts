export const formatNumber = (value: number) => {
  return new Intl.NumberFormat('en-US', {
    notation: 'compact',
    compactDisplay: 'short',
    maximumFractionDigits: 1,
  }).format(value);
};

export const formatCurrency = (number: number, locale?: string, currency?: string) => {
  const formatter = new Intl.NumberFormat(locale ?? 'en-US', {
    style: 'currency',
    currency: currency ?? 'USD',
    maximumFractionDigits: 0,
    minimumFractionDigits: 0,
  });

  return formatter.format(number);
};

export function getSupplierUrl(slug: string, path: string = '') {
  if (typeof window !== 'undefined') {
    const host = window.location.host;
    const protocol = window.location.protocol;
    return `${protocol}//${slug}.${host.split('.').slice(-2).join('.')}${path}`;
  }
  return `https://${slug}.mydomain.com${path}`;
}
