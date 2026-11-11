import { describe, expect, it } from 'vitest';
import { applyChange, averageOf, countOf, emptySummary } from './rating-summary.js';
import { maskName, Review } from './review.js';

const PREFIX = 'https://cdn.example/media/';
const base = { id: 'r1', productId: 'p1', customerId: 'c1', authorName: 'Alex Johnson', orderId: 'o1' };
const t0 = new Date('2026-01-01T00:00:00Z');

describe('Review', () => {
  it('kiểm tra số sao và media', () => {
    expect(() => Review.submit(base, { rating: 6 }, PREFIX, t0)).toThrow(/rating/);
    expect(() => Review.submit(base, { rating: 4.5 }, PREFIX, t0)).toThrow(/rating/);
    expect(() => Review.submit(base, { rating: 5, mediaUrls: ['https://evil/x.png'] }, PREFIX, t0)).toThrow(
      /file-service/,
    );
    const r = Review.submit(
      base,
      { rating: 5, content: '  Tốt  ', mediaUrls: [`${PREFIX}a.jpg`, `${PREFIX}a.jpg`] },
      PREFIX,
      t0,
    );
    expect(r.snapshot()).toMatchObject({ content: 'Tốt', mediaUrls: [`${PREFIX}a.jpg`], status: 'PUBLISHED' });
  });

  it('chỉ tác giả sửa được, trong 30 ngày', () => {
    const r = Review.submit(base, { rating: 3 }, PREFIX, t0);
    expect(() => r.edit('c2', { rating: 4 }, PREFIX, t0)).toThrow(/author/);
    expect(() => r.edit('c1', { rating: 4 }, PREFIX, new Date('2026-03-01'))).toThrow(/30 days/);
    r.edit('c1', { rating: 4 }, PREFIX, new Date('2026-01-10'));
    expect(r.rating).toBe(4);
  });

  it('ẩn phải có lý do; seller trả lời một lần', () => {
    const r = Review.submit(base, { rating: 1 }, PREFIX, t0);
    expect(() => r.moderate('HIDDEN', ' ', t0)).toThrow(/reason/);
    r.reply('Cảm ơn bạn', t0);
    expect(() => r.reply('lần 2', t0)).toThrow(/already/);
  });

  it('che tên', () => {
    expect(maskName('Alex Johnson')).toBe('A***n');
    expect(maskName('A')).toBe('A*');
  });
});

describe('RatingSummary', () => {
  it('cộng, đổi, bỏ sao', () => {
    let s = emptySummary('p1');
    s = applyChange(s, null, 5);
    s = applyChange(s, null, 4);
    s = applyChange(s, null, 3);
    expect(averageOf(s)).toBe(4);
    s = applyChange(s, 3, 1);
    expect(s.stars).toEqual([1, 0, 0, 1, 1]);
    s = applyChange(s, 1, null);
    expect(countOf(s)).toBe(2);
    expect(averageOf(s)).toBe(4.5);
  });
});
