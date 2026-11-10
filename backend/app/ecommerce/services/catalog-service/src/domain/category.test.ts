import { describe, expect, it } from 'vitest';
import { Category, checkDepth } from './category.js';
import { slugify } from './slug.js';
import { normalizeTagName } from './tag.js';

const now = new Date('2026-01-01T00:00:00Z');

describe('slugify', () => {
  it('bỏ dấu tiếng Việt và ký tự đặc biệt', () => {
    expect(slugify('Điện thoại & Máy tính bảng')).toBe('dien-thoai-may-tinh-bang');
    expect(slugify('  Sách -- Văn học  ')).toBe('sach-van-hoc');
  });
});

describe('Category', () => {
  it('tạo với slug sinh từ tên', () => {
    const c = Category.create('c1', { name: ' Sách giáo khoa ' }, now);
    expect(c.snapshot()).toMatchObject({
      name: 'Sách giáo khoa',
      slug: 'sach-giao-khoa',
      status: 'ACTIVE',
      version: 0,
    });
  });

  it('từ chối tên rỗng', () => {
    expect(() => Category.create('c1', { name: '   ' }, now)).toThrow(/name is required/);
  });

  it('chặn chuyển vào nhánh con của chính nó', () => {
    const c = Category.create('a', { name: 'A' }, now);
    expect(() => c.moveTo('b', ['a', 'b'], now)).toThrow(/under itself/);
    expect(() => c.moveTo('a', [], now)).toThrow(/under itself/);
  });

  it('giới hạn cây 5 cấp', () => {
    const c = Category.create('x', { name: 'X' }, now);
    expect(() => c.moveTo('p4', ['p1', 'p2', 'p3', 'p4', 'p5'], now)).toThrow(/5 levels/);
    expect(() => checkDepth(['p1', 'p2', 'p3', 'p4'])).not.toThrow();
  });

  it('ẩn/hiện idempotent', () => {
    const c = Category.create('x', { name: 'X' }, now);
    c.deactivate(now);
    c.deactivate(now);
    expect(c.status).toBe('INACTIVE');
    c.activate(now);
    expect(c.status).toBe('ACTIVE');
  });
});

describe('tag', () => {
  it('chuẩn hoá tên tag', () => {
    expect(normalizeTagName('  Giảm   Giá ')).toBe('giảm giá');
  });
});
