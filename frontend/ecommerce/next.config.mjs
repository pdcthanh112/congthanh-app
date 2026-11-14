import { withSentryConfig } from '@sentry/nextjs';
import createNextIntlPlugin from 'next-intl/plugin';

const withNextIntl = createNextIntlPlugin();

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: 'standalone',
  reactStrictMode: true,
  // "/" do middleware next-intl chuyển sang "/<locale>", rồi app/[locale]/page.tsx chuyển tiếp tới /home
  // (redirect tạm thời, không cache vĩnh viễn trong trình duyệt như redirect permanent cũ).
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
};

export default withSentryConfig(withNextIntl(nextConfig), {
  // https://www.npmjs.com/package/@sentry/webpack-plugin#options
  org: 'fpt-software-0g',
  project: 'congthanhapp-ecommerce',
  authToken: process.env.SENTRY_AUTH_TOKEN,
  // Chỉ in log upload source map trên CI.
  silent: !process.env.CI,
  widenClientFileUpload: true,
  // Gửi sự kiện Sentry qua /monitoring (cùng origin) để tránh bị ad-blocker chặn; middleware đã loại trừ route này.
  tunnelRoute: '/monitoring',
  disableLogger: true,
  automaticVercelMonitors: true,
});
