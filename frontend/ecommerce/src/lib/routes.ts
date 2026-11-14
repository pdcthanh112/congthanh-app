import { PATH } from '@/utils/constants/path';

export const LOGIN = PATH.AUTH_PATH_URL.LOGIN;

/**
 * Các route cần đăng nhập (so khớp sau khi bỏ prefix locale, theo đoạn path: '/order' khớp '/order' và '/order/x',
 * không khớp '/orders-guide'). Mặc định route nào không có ở đây là public (trang chủ, sản phẩm, danh mục, auth...).
 */
export const PROTECTED_ROUTES = [
  '/cart',
  '/checkout',
  '/manage',
  '/order',
  '/payment',
  '/wishlist',
  '/notification',
] as const;

export const isProtectedPath = (path: string) =>
  PROTECTED_ROUTES.some((route) => path === route || path.startsWith(`${route}/`));
