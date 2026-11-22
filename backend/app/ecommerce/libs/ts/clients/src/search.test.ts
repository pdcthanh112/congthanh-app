import { describe, expect, it } from 'vitest';
import { toParams } from './search.js';

describe('toParams', () => {
  it('đổi bộ lọc sang tham số search-service, nhiều giá trị nối dấu phẩy, bỏ bộ lọc rỗng', () => {
    expect(
      toParams({
        text: 'iphone',
        brandIds: ['7', '9'],
        attributes: { ram: ['8 GB', '16 GB'], empty: [] },
        options: { Color: ['Black'] },
        currency: 'VND',
        minPrice: 0,
        page: 0,
      }),
    ).toEqual({
      q: 'iphone',
      brand: '7,9',
      'attr.ram': '8 GB,16 GB',
      'opt.Color': 'Black',
      currency: 'VND',
      minPrice: 0,
      page: 0,
    });
  });
});
