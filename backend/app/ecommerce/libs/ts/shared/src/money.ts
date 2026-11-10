export interface Money {
  amountMinor: number;
  currency: string;
}

export const vnd = (amount: number): Money => ({ amountMinor: amount, currency: 'VND' });

/** Số chữ số phần lẻ của đơn vị tiền tệ theo ISO 4217 (VND 0, USD 2...). */
export function minorDigits(currency: string): number {
  return new Intl.NumberFormat('en', { style: 'currency', currency }).resolvedOptions().maximumFractionDigits ?? 2;
}

/** Từ số thập phân (REST/gRPC trả double hoặc chuỗi BigDecimal) sang đơn vị nhỏ nhất, làm tròn. */
export function fromDecimal(amount: number | string, currency: string): Money {
  return { amountMinor: Math.round(Number(amount) * 10 ** minorDigits(currency)), currency };
}

export function toDecimal(m: Money): number {
  return m.amountMinor / 10 ** minorDigits(m.currency);
}

/** Từ ecommerce.common.v1.Money (units + nanos). */
export function fromProtoMoney(m: { currencyCode: string; units: bigint; nanos: number }): Money {
  return fromDecimal(Number(m.units) + m.nanos / 1e9, m.currencyCode || 'VND');
}

/** Sang ecommerce.common.v1.Money. */
export function toProtoMoney(m: Money): { currencyCode: string; units: bigint; nanos: number } {
  const value = toDecimal(m);
  const units = Math.trunc(value);
  return { currencyCode: m.currency, units: BigInt(units), nanos: Math.round((value - units) * 1e9) };
}

export function multiplyMoney(m: Money, factor: number): Money {
  return { amountMinor: Math.round(m.amountMinor * factor), currency: m.currency };
}

export function sumMoney(items: readonly Money[], currency = 'VND'): Money {
  const mixed = items.find((i) => i.currency !== (items[0]?.currency ?? currency));
  if (mixed) throw new Error(`cannot add ${mixed.currency} to ${items[0]?.currency}`);
  return { amountMinor: items.reduce((a, i) => a + i.amountMinor, 0), currency: items[0]?.currency ?? currency };
}

export function formatMoney(m: Money, locale = 'vi-VN'): string {
  return new Intl.NumberFormat(locale, { style: 'currency', currency: m.currency }).format(toDecimal(m));
}
