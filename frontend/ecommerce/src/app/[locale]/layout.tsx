import '../globals.css';
import { Inter } from 'next/font/google';
import AppHeader from '@/components/app-component/AppHeader';
import AppFooter from '@/components/app-component/AppFooter';
import AppNavbar from '@/components/app-component/AppNavbar';
import Providers from '../providers';
import { RootLayout } from '@/layout';
import { type Locale } from '@/lib/locales';
import { getTranslations } from 'next-intl/server';
import { Metadata } from 'next';
import { routing } from '@/i18n/routing';
import { notFound } from 'next/navigation';
import { getMessages } from 'next-intl/server';
import { NextIntlClientProvider } from 'next-intl';
import AppContent from '@/components/app-component/AppContent';

const inter = Inter({ subsets: ['latin'] });

export const baseOpenGraph = {
  locale: 'en_US',
  type: 'website',
  siteName: 'CongThanhApp - Ecommerce',
};

export async function generateMetadata({ params }: { params: Promise<{ locale: Locale }> }): Promise<Metadata> {
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

type LayoutPropsType = {
  children: React.ReactNode;
  params: {
    locale: Locale;
  };
};

export default async function Layout({ children, params: { locale } }: Readonly<LayoutPropsType>) {
  if (!routing.locales.includes(locale)) {
    notFound();
  }

  const messages = await getMessages();

  return (
    <html lang={locale} suppressHydrationWarning>
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1" />
      </head>
      <body className={inter.className}>
        <Providers>
          <NextIntlClientProvider messages={messages}>
            <AppHeader />
            <AppNavbar />
            <AppContent>
              <RootLayout>{children}</RootLayout>
            </AppContent>
            <AppFooter />
          </NextIntlClientProvider>
        </Providers>
      </body>
    </html>
  );
}
