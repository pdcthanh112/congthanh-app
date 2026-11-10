export interface Country {
  id: number;
  isoCode: string;
  isoCode3: string | null;
  countryCode: string | null;
  name: string;
  nameOrigin: string | null;
  phoneCode: string | null;
  currencyCode: string | null;
  currencySymbol: string | null;
  language: string | null;
  locale: string | null;
  timezone: string | null;
}
