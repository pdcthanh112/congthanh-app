// import { withModuleFederation } from '@module-federation/nextjs-mf';

// import type { NextConfig } from "next";

// const nextConfig: NextConfig = {
//   /* config options here */
// };

// export default withModuleFederation(nextConfig);
const { withModuleFederation } = require('@module-federation/nextjs-mf');

module.exports = withModuleFederation({
  name: 'app-shell', // Tên của app shell, dùng để expose cho các MFE khác
  remotes: {
    header: 'header@http://localhost:3001/remoteEntry.js', // Đường dẫn tới remote của Header MFE
    productListing: 'product_listing@http://localhost:3002/remoteEntry.js',
    productDetail: 'product_detail@http://localhost:3003/remoteEntry.js',
    cartCheckout: 'cart_checkout@http://localhost:3004/remoteEntry.js',
    userAccount: 'user_account@http://localhost:3005/remoteEntry.js',
    marketing: 'marketing@http://localhost:3006/remoteEntry.js',
  },
  shared: {
    react: { singleton: true, eager: true, requiredVersion: '^18.2.0' }, // Đảm bảo phiên bản React đồng nhất
    'react-dom': { singleton: true, eager: true, requiredVersion: '^18.2.0' },
  },
})({
  reactStrictMode: true,
  swcMinify: true, // Tối ưu hóa build
  images: {
    domains: ['your-cdn-domain.com'], // Thêm domain CDN nếu dùng hình ảnh
  },
  // Cấu hình môi trường production
  env: {
    API_URL: process.env.API_URL || 'http://localhost:8080', // URL BFF hoặc API
  },
});
