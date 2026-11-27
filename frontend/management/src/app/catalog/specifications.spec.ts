import { fromDraft, toDraft } from './specifications';

describe('specifications draft', () => {
  it('bỏ dòng trống và nhóm rỗng, tên nhóm trống thành null', () => {
    const groups = fromDraft([
      {
        key: 1,
        name: ' Màn hình ',
        items: [
          { key: 2, name: ' Kích thước ', value: ' 6.7 inch ' },
          { key: 3, name: ' ', value: '' },
        ],
      },
      { key: 4, name: 'Rỗng', items: [{ key: 5, name: '', value: '' }] },
      { key: 6, name: ' ', items: [{ key: 7, name: 'Bảo hành', value: '12 tháng' }] },
    ]);

    expect(groups).toEqual([
      { name: 'Màn hình', items: [{ name: 'Kích thước', value: '6.7 inch' }] },
      { name: null, items: [{ name: 'Bảo hành', value: '12 tháng' }] },
    ]);
  });

  it('chuyển qua lại giữ nguyên nội dung', () => {
    const original = [{ name: null, items: [{ name: 'Xuất xứ', value: 'Việt Nam' }] }];
    expect(fromDraft(toDraft(original))).toEqual(original);
  });
});
