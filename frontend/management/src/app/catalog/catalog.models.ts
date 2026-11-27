/** Kiểu dữ liệu của API quản trị thông số (product-service, ADR 0033). */

export const ATTRIBUTE_DATA_TYPES = ['TEXT', 'NUMBER', 'BOOLEAN', 'MULTI_SELECT'] as const;
export type AttributeDataType = (typeof ATTRIBUTE_DATA_TYPES)[number];

export const DATA_TYPE_LABELS: Record<AttributeDataType, string> = {
  TEXT: 'Chữ',
  NUMBER: 'Số',
  BOOLEAN: 'Có/Không',
  MULTI_SELECT: 'Chọn nhiều',
};

export interface ProductAttributeDef {
  id: number | null;
  code: string | null;
  name: string;
  dataType: AttributeDataType;
  unit: string | null;
  filterable: boolean;
  searchable: boolean;
  displayOrder: number;
  groupId: number | null;
  /** Giá trị cho phép (TEXT/MULTI_SELECT); rỗng = nhập tự do. */
  options: string[];
}

export interface AttributeGroup {
  id: number;
  name: string;
  displayOrder: number;
}

export interface CategoryOption {
  id: string;
  name: string;
}

export interface CategoryTemplateRow {
  attributeId: number;
  name: string;
  dataType: AttributeDataType;
  required: boolean;
  displayOrder: number;
  groupName: string | null;
}

export interface SpecificationGroup {
  /** null = nhóm không tiêu đề. */
  name: string | null;
  items: { name: string; value: string }[];
}

export interface AdminProduct {
  id: string;
  name: string;
  version: number;
  specifications: SpecificationGroup[];
}
