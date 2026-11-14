import '../globals.css';
import '@/lib/auth/register-server-token';
import { Inter } from 'next/font/google';
import { Metadata } from 'next';
import { notFound } from 'next/navigation';
import { NextIntlClientProvider } from 'next-intl';
import { getMessages, getTranslations, setRequestLocale } from 'next-intl/server';
import AppHeader from '@/components/app-component/AppHeader';
import AppFooter from '@/components/app-component/AppFooter';
import AppNavbar from '@/components/app-component/AppNavbar';
import AppContent from '@/components/app-component/AppContent';
import Providers from '../providers';
import { RootLayout } from '@/layout';
import { isValidLocale, locales, type Locale } from '@/lib/locales';

const inter = Inter({ subsets: ['latin'] });

const baseOpenGraph = {
  locale: 'en_US',
  type: 'website',
  siteName: 'CongThanhApp - Ecommerce',
};

type Params = Promise<{ locale: Locale }>;

export function generateStaticParams() {
  return locales.map((locale) => ({ locale }));
}

export async function generateMetadata({ params }: { params: Params }): Promise<Metadata> {
  const { locale } = await params;
  const t = await getTranslations({ locale });

  return {
    title: t('metadata.title'),
    description: t('metadata.description'),
    openGraph: {
      ...baseOpenGraph,
      title: t('metadata.title'),
      description: t('metadata.description'),
      url: 'localhost:3000',
      images: [
        {
          url: 'https://th.bing.com/th/id/R.ca0f4aeff51ff7e15c440816546f7730?rik=eJ5AEYKPVEnIIA&pid=ImgRaw&r=0&sres=1&sresct=1',
        },
      ],
    },
    alternates: {
      canonical: 'localhost:3000',
    },
  };
}

export default async function Layout({ children, params }: { children: React.ReactNode; params: Params }) {
  const { locale } = await params;
  if (!isValidLocale(locale)) notFound();
  // Cho phép render tĩnh các trang không phụ thuộc request.
  setRequestLocale(locale);

  const messages = await getMessages();

  return (
    <html lang={locale} suppressHydrationWarning>
      <body className={inter.className}>
        <NextIntlClientProvider locale={locale} messages={messages}>
          <Providers>
            <AppHeader />
            <AppNavbar />
            <AppContent>
              <RootLayout>{children}</RootLayout>
            </AppContent>
            <AppFooter />
          </Providers>
        </NextIntlClientProvider>
      </body>
    </html>
  );
}
