/** Khoảng làm mới sớm trước khi access token hết hạn. */
export const REFRESH_MARGIN_SECONDS = 60;
/** Chu kỳ SessionProvider gọi lại /api/auth/session, phải nhỏ hơn REFRESH_MARGIN_SECONDS. */
export const SESSION_REFETCH_SECONDS = 45;
