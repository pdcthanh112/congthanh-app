/** Điểm tổng hợp của một sản phẩm; cập nhật cộng dồn trong cùng transaction với đánh giá. */
export interface RatingSummary {
  productId: string;
  /** Số lượng theo sao, index 0 = 1 sao ... index 4 = 5 sao. */
  stars: [number, number, number, number, number];
}

export const emptySummary = (productId: string): RatingSummary => ({ productId, stars: [0, 0, 0, 0, 0] });

export function countOf(s: RatingSummary): number {
  return s.stars.reduce((a, b) => a + b, 0);
}

export function averageOf(s: RatingSummary): number {
  const n = countOf(s);
  if (n === 0) return 0;
  const sum = s.stars.reduce((acc, c, i) => acc + c * (i + 1), 0);
  return Math.round((sum / n) * 10) / 10;
}

/** Áp thay đổi: thêm (+1 sao mới), bỏ (−1 sao cũ), hoặc đổi sao. Đánh giá bị ẩn không tính. */
export function applyChange(s: RatingSummary, before: number | null, after: number | null): RatingSummary {
  const stars = [...s.stars] as RatingSummary['stars'];
  if (before) stars[before - 1] = Math.max(0, stars[before - 1]! - 1);
  if (after) stars[after - 1] = stars[after - 1]! + 1;
  return { ...s, stars };
}
