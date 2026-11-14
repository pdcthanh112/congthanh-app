import { expect, test } from '@playwright/test';

test.describe('i18n', () => {
  test('/ chuyển tới trang chủ có tiền tố ngôn ngữ mặc định', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/en\/home$/);
  });

  test('đổi ngôn ngữ qua URL', async ({ page }) => {
    await page.goto('/vi/home');
    await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  });
});

test.describe('Bảo vệ route', () => {
  test('khách chưa đăng nhập vào giỏ hàng bị chuyển tới đăng nhập kèm callbackUrl', async ({ page }) => {
    await page.goto('/en/cart');
    await expect(page).toHaveURL(/\/en\/auth\/login\?callbackUrl=%2Fen%2Fcart/);
  });
});
