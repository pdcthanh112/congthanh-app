import { getRequestConfig } from 'next-intl/server';
import { isValidLocale } from '@/lib/locales';
import { routing } from './routing';

type Messages = { [key: string]: string | Messages };

/** Gộp sâu: khoá còn thiếu ở ngôn ngữ đang chọn lấy từ tiếng Anh thay vì hiện tên khoá. */
function withFallback(base: Messages, override: Messages): Messages {
  const out: Messages = { ...base };
  for (const [k, v] of Object.entries(override)) {
    const b = base[k];
    out[k] = typeof v === 'object' && typeof b === 'object' ? withFallback(b, v) : v;
  }
  return out;
}

export default getRequestConfig(async ({ requestLocale }) => {
  const requested = await requestLocale;
  const locale = requested && isValidLocale(requested) ? requested : routing.defaultLocale;

  const fallback = (await import(`../locales/${routing.defaultLocale}/message.json`)).default as Messages;
  const messages =
    locale === routing.defaultLocale
      ? fallback
      : withFallback(fallback, (await import(`../locales/${locale}/message.json`)).default as Messages);

  return { locale, messages };
});
