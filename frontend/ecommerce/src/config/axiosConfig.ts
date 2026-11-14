import axios from 'axios';
import { GATEWAY_URL, serverToken } from '@/graphql/client';

/**
 * REST cho các API không nằm trong supergraph (search, supplier, media, payment...).
 * Trình duyệt gọi qua BFF /api/gateway (token gắn phía server); server gọi thẳng gateway kèm token từ cookie.
 */
const axiosInstance = axios.create({
  baseURL: typeof window === 'undefined' ? `${GATEWAY_URL}/api/ecommerce/` : '/api/gateway/',
  headers: { 'Content-Type': 'application/json' },
});

axiosInstance.interceptors.request.use(async (config) => {
  if (typeof window === 'undefined' && !config.headers.Authorization) {
    const token = await serverToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default axiosInstance;
