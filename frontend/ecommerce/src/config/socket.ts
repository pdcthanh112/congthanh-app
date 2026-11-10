const LOCAL_SOCKET_HOST = 'http://localhost:5000';
const DEV_SOCKET_HOST = 'https://socket.mydream.dev';
const STAGING_SOCKET_HOST = 'https://socket.mydream.staging';
const PRODUCTION_SOCKET_HOST = 'https://socket.mydream.production';

const getSocketServer = () => {
  switch (process.env.NEXT_PUBLIC_APP_ENV) {
    case 'production':
      return PRODUCTION_SOCKET_HOST;
    case 'staging':
      return STAGING_SOCKET_HOST;
    case 'local':
      return LOCAL_SOCKET_HOST;
    default:
      return DEV_SOCKET_HOST;
  }
};

export default getSocketServer;