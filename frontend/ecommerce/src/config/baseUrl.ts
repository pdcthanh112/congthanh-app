const LOCAL_API_HOST = 'http://localhost:5000';
const DEV_API_HOST = 'https://api.mydream.dev';
const STAGING_API_HOST = 'https://api.mydream.staging';
const PRODUCTION_API_HOST = 'https://api.mydream.production';

const getBaseUrl = () => {
  switch (process.env.NEXT_PUBLIC_APP_ENV) {
    case 'production':
      return PRODUCTION_API_HOST;
    case 'staging':
      return STAGING_API_HOST;
    case 'local':
      return LOCAL_API_HOST;
    default:
      return DEV_API_HOST;
  }
};

export default getBaseUrl;