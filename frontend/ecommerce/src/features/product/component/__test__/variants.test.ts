import { describe, expect, it } from 'vitest';
import type { ProductVariant } from '@/graphql/types';
import { findVariant, isValueAvailable, selectionOf } from '../product-detail/variants';

const variant = (id: string, color: string, size: string, available: number | null): ProductVariant => ({
  id,
  name: id,
  slug: null,
  sku: null,
  price: null,
  thumbnail: null,
  images: [],
  options: [
    { optionId: '1', name: 'Màu', value: color },
    { optionId: '2', name: 'Size', value: size },
  ],
  stock: available === null ? null : { available, status: available > 0 ? 'IN_STOCK' : 'OUT_OF_STOCK' },
});

const variants = [variant('red-m', 'Đỏ', 'M', 3), variant('red-l', 'Đỏ', 'L', 0), variant('blue-l', 'Xanh', 'L', null)];

describe('chọn biến thể', () => {
  it('chưa chọn đủ trục thì chưa có biến thể', () => {
    expect(findVariant(variants, 2, { '1': 'Đỏ' })).toBeUndefined();
  });

  it('chọn đủ trục thì ra đúng biến thể', () => {
    expect(findVariant(variants, 2, { '1': 'Đỏ', '2': 'L' })?.id).toBe('red-l');
  });

  it('tổ hợp không tồn tại thì không có biến thể', () => {
    expect(findVariant(variants, 2, { '1': 'Xanh', '2': 'M' })).toBeUndefined();
  });

  it('chọn sẵn theo biến thể từ ?pvid=', () => {
    expect(selectionOf(variants[2])).toEqual({ '1': 'Xanh', '2': 'L' });
    expect(selectionOf(undefined)).toEqual({});
  });

  it('giá trị chỉ còn biến thể hết hàng thì không chọn được', () => {
    expect(isValueAvailable(variants, { '1': 'Đỏ' }, '2', 'L')).toBe(false);
    expect(isValueAvailable(variants, { '1': 'Đỏ' }, '2', 'M')).toBe(true);
  });

  it('biến thể chưa rõ tồn kho vẫn chọn được', () => {
    expect(isValueAvailable(variants, { '2': 'L' }, '1', 'Xanh')).toBe(true);
  });
});

describe('nhóm thông số', () => {
  it('gom dòng liền nhau cùng nhóm, giữ thứ tự', async () => {
    const { groupSpecifications } = await import('../product-detail/ProductSpecifications');
    const groups = groupSpecifications([
      { group: 'Màn hình', name: 'Kích thước', value: '6.7 inch' },
      { group: 'Màn hình', name: 'Tần số quét', value: '120Hz' },
      { group: 'Pin', name: 'Dung lượng', value: '5000 mAh' },
      { group: null, name: 'Xuất xứ', value: 'Việt Nam' },
    ]);
    expect(groups.map((g) => [g.group, g.rows.length])).toEqual([
      ['Màn hình', 2],
      ['Pin', 1],
      [null, 1],
    ]);
  });

  it('thêm thông số tự do sau thuộc tính, bỏ dòng trùng tên thuộc tính', async () => {
    const { groupSpecifications } = await import('../product-detail/ProductSpecifications');
    const groups = groupSpecifications(
      [{ group: null, name: 'Xuất xứ', value: 'Việt Nam' }],
      [
        {
          name: 'Bảo quản',
          items: [
            { name: 'Giặt', value: 'Nước lạnh' },
            { name: 'xuất xứ', value: 'VN' },
          ],
        },
        { name: 'Trùng hết', items: [{ name: 'Xuất xứ', value: 'VN' }] },
      ]
    );
    expect(groups).toEqual([
      { group: null, rows: [{ group: null, name: 'Xuất xứ', value: 'Việt Nam' }] },
      { group: 'Bảo quản', rows: [{ name: 'Giặt', value: 'Nước lạnh' }] },
    ]);
  });
});
