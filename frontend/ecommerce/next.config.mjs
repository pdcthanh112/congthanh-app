import { withSentryConfig } from '@sentry/nextjs';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin();

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  // experimental: {
  //   dynamicIO: true,
  //   cacheLife: {
  //     getAllProduct: {
  //       stale: undefined, // No stale data
  //       revalidate: 1 * 60, // Revalidate every 1 minute
  //       expire: 15 * 60, // Expire after 15 minute
  //     },
  //     dailySummary: {
  //       stale: 5,
  //       revalidate: 30,
  //       expire: 120,
  //     },
  //     historicalReports: {
  //       stale: 30, // Keep stale for 30 seconds
  //       revalidate: 60,
  //       expire: 600,
  //     },
  //   },
  // },
  async redirects() {
    return [
      {
        source: '/',
        destination: '/home',
        permanent: true,
      },
    ];
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  logging: {
    fetches: {
      fullUrl: true,
    },
  },
  images: {
    remotePatterns: [
      {
        protocol: 'https',
        hostname: '*',
      },
    ],
  },
  // i18n: {
  //   locales: ['en', 'es', 'vi'],
  //   defaultLocale: 'en',
  //   localeDetection: true
  // },
};

export default withSentryConfig(withSentryConfig(withNextIntl(nextConfig), {
// For all available options, see:
// https://github.com/getsentry/sentry-webpack-plugin#options

org: 'fpt-software-0g',
project: 'congthanhapp-ecommerce',

// Only print logs for uploading source maps in CI
silent: !process.env.CI,

// For all available options, see:
// https://docs.sentry.io/platforms/javascript/guides/nextjs/manual-setup/

// Upload a larger set of source maps for prettier stack traces (increases build time)
widenClientFileUpload: true,

// Uncomment to route browser requests to Sentry through a Next.js rewrite to circumvent ad-blockers.
// This can increase your server load as well as your hosting bill.
// Note: Check that the configured route will not match with your Next.js middleware, otherwise reporting of client-
// side errors will fail.
// tunnelRoute: "/monitoring",

// Automatically tree-shake Sentry logger statements to reduce bundle size
disableLogger: true,
authToken: process.env.SENTRY_AUTH_TOKEN,
// Enables automatic instrumentation of Vercel Cron Monitors. (Does not yet work with App Router route handlers.)
// See the following for more information:
// https://docs.sentry.io/product/crons/
// https://vercel.com/docs/cron-jobs
automaticVercelMonitors: true,
}), {
// For all available options, see:
// https://www.npmjs.com/package/@sentry/webpack-plugin#options

org: "fpt-software-0g",
project: "congthanhapp-ecommerce",

// Only print logs for uploading source maps in CI
silent: !process.env.CI,

// For all available options, see:
// https://docs.sentry.io/platforms/javascript/guides/nextjs/manual-setup/

// Upload a larger set of source maps for prettier stack traces (increases build time)
widenClientFileUpload: true,

// Route browser requests to Sentry through a Next.js rewrite to circumvent ad-blockers.
// This can increase your server load as well as your hosting bill.
// Note: Check that the configured route will not match with your Next.js middleware, otherwise reporting of client-
// side errors will fail.
tunnelRoute: "/monitoring",

// Automatically tree-shake Sentry logger statements to reduce bundle size
disableLogger: true,

// Enables automatic instrumentation of Vercel Cron Monitors. (Does not yet work with App Router route handlers.)
// See the following for more information:
// https://docs.sentry.io/product/crons/
// https://vercel.com/docs/cron-jobs
automaticVercelMonitors: true,
});