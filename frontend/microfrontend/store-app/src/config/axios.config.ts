import axios from 'axios';
import { ENV_CONFIG } from './env.config';

export const axiosInstance = axios.create({
  baseURL: ENV_CONFIG.API_BASE_URL,
  withCredentials: true,
  timeout: 10000,
  headers: {
    'Content-Type': 'application/json',
  },
});

axiosInstance.interceptors.request.use(config => {
  const token = getAccessToken()
})
