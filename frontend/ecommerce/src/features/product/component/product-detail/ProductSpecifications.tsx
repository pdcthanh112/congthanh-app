import type { ProductAttribute, ProductSpecificationGroup } from '@/graphql/types';
import { useTranslations } from 'next-intl';

type Row = { name: string; value: string };

/**
 * Ghép bảng thông số: trước là thuộc tính có cấu trúc (BFF đã sắp theo nhóm rồi thứ tự), gom các dòng liền nhau cùng
 * nhóm; sau là thông số tự do. Dòng tự do trùng tên một thuộc tính (không phân biệt hoa thường) bị bỏ vì thuộc tính
 * có cấu trúc là nguồn chính.
 */
export function groupSpecifications(
  attributes: readonly ProductAttribute[],
  specifications: readonly ProductSpecificationGroup[] = []
) {
  const groups: { group: string | null; rows: Row[] }[] = [];
  for (const a of attributes) {
    const last = groups.at(-1);
    if (last && last.group === a.group) last.rows.push(a);
    else groups.push({ group: a.group, rows: [a] });
  }
  const known = new Set(attributes.map((a) => a.name.toLowerCase()));
  for (const g of specifications) {
    const rows = g.items.filter((i) => !known.has(i.name.toLowerCase()));
    if (rows.length > 0) groups.push({ group: g.name, rows });
  }
  return groups;
}

interface ProductSpecificationsProps {
  attributes: ProductAttribute[];
  specifications: ProductSpecificationGroup[];
}

export default function ProductSpecifications({ attributes, specifications }: ProductSpecificationsProps) {
  const t = useTranslations();
  const groups = groupSpecifications(attributes, specifications);
  if (groups.length === 0) return null;

  return (
    <section className="bg-white mt-6 p-5">
      <h2 className="bg-yellow-100 px-2 py-1 rounded-sm uppercase">{t('product.product_attribute')}</h2>
      <table className="w-full mt-3 text-sm">
        {groups.map(({ group, rows }, index) => (
          <tbody key={`${group ?? ''}-${index}`} className="divide-y">
            {group && (
              <tr>
                <th colSpan={2} scope="colgroup" className="pt-4 pb-2 text-left font-medium">
                  {group}
                </th>
              </tr>
            )}
            {rows.map((item) => (
              <tr key={item.name}>
                <th scope="row" className="text-left font-normal py-2 pr-4 w-1/3 text-gray-500">
                  {item.name}
                </th>
                <td className="py-2">{item.value}</td>
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </section>
  );
}
