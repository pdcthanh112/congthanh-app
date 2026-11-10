export const ENV_CONFIG = {
  APP_ENV: import.meta.env.MODE || 'development',
  API_BASE_URL: import.meta.env.VITE_API_BASE_URL,
};
