import axios from 'axios';
import { accessToken, GATEWAY_URL } from '@/graphql/client';

// REST qua gateway cho các API không nằm trong supergraph (search, supplier, media, translate, chatbot).
// Dữ liệu chính (sản phẩm, giỏ, đơn, khách hàng...) gọi GraphQL qua @/graphql.
const axiosInstance = axios.create({
  baseURL: `${GATEWAY_URL}/api/ecommerce/`,
  headers: { 'Content-Type': 'application/json' },
});

axiosInstance.interceptors.request.use(async (config) => {
  if (!config.headers.Authorization) {
    const token = await accessToken();
    if (token) config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

export default axiosInstance;
