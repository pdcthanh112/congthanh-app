import React from 'react';
import { Link } from '@/i18n/routing';

/** Link sản phẩm trợ lý viết: dạng markdown [tên](/product/slug) hoặc đường dẫn trần /product/slug. */
const PRODUCT_LINK = /\[([^\]]+)\]\((\/product\/[A-Za-z0-9_-]+)\)|(\/product\/[A-Za-z0-9_-]+)/g;

/**
 * Hiển thị câu trả lời dạng chữ thường: chỉ biến link sản phẩm nội bộ thành Link, bỏ dấu ** của markdown.
 * Không render HTML hay link ngoài từ model (chống chèn link độc qua dữ liệu sản phẩm).
 */
export default function MessageText({ text }: { text: string }) {
  const parts: React.ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(PRODUCT_LINK)) {
    const index = match.index ?? 0;
    parts.push(text.slice(last, index));
    const href = match[2] ?? match[3] ?? '';
    parts.push(
      <Link key={index} href={href} className="text-blue-600 underline">
        {match[1] ?? href}
      </Link>
    );
    last = index + match[0].length;
  }
  parts.push(text.slice(last));
  return (
    <p className="whitespace-pre-wrap break-words">
      {parts.map((p) => (typeof p === 'string' ? p.replaceAll('**', '') : p))}
    </p>
  );
}
