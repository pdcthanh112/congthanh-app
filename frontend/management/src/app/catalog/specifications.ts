import { SpecificationGroup } from './catalog.models';

/** Bản nháp đang sửa: có key ổn định cho từng nhóm/dòng để Angular không dựng lại input khi gõ. */
export interface DraftGroup {
  key: number;
  name: string;
  items: { key: number; name: string; value: string }[];
}

let nextKey = 0;
export const newKey = (): number => ++nextKey;

export function toDraft(groups: SpecificationGroup[]): DraftGroup[] {
  return groups.map((g) => ({
    key: newKey(),
    name: g.name ?? '',
    items: g.items.map((i) => ({ key: newKey(), name: i.name, value: i.value })),
  }));
}

/** Bỏ dòng trống hoàn toàn và nhóm không còn dòng; tên nhóm trống thành null. Server kiểm phần còn lại. */
export function fromDraft(groups: DraftGroup[]): SpecificationGroup[] {
  return groups
    .map((g) => ({
      name: g.name.trim() || null,
      items: g.items
        .filter((i) => i.name.trim() || i.value.trim())
        .map((i) => ({ name: i.name.trim(), value: i.value.trim() })),
    }))
    .filter((g) => g.items.length > 0);
}
