import { PATH } from "@/utils/constants/path";

export const LOGIN = PATH.AUTH_PATH_URL.LOGIN;
export const ROOT = PATH.HOME;

export const PUBLIC_ROUTES = [
    '/auth/login',
    '/auth/register',
    '/products',
    '/api/auth/callback/google',
    '/api/auth/callback/github',
]

export const PROTECTED_SUB_ROUTES = [
    '/checkout',
]