'use client';

import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useTranslations } from 'next-intl';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

type Props = {
  page: number;
  totalPage: number;
  onChange: (page: number) => void;
  className?: string;
};

/** Trả về dãy trang hiển thị, ví dụ [1, '…', 4, 5, 6, '…', 20]. */
export function pageRange(page: number, total: number, sibling = 1): (number | '…')[] {
  const pages: (number | '…')[] = [];
  const start = Math.max(2, page - sibling);
  const end = Math.min(total - 1, page + sibling);
  pages.push(1);
  if (start > 2) pages.push('…');
  for (let i = start; i <= end; i++) pages.push(i);
  if (end < total - 1) pages.push('…');
  if (total > 1) pages.push(total);
  return pages;
}

/** Phân trang bằng nút (state phía client). Thay cho Pagination của MUI / antd. */
export default function PaginationComponent({ page, totalPage, onChange, className }: Props) {
  const t = useTranslations('common');
  if (totalPage <= 1) return null;

  const btn = (active = false) => cn(buttonVariants({ variant: active ? 'outline' : 'ghost', size: 'icon' }));

  return (
    <nav aria-label={t('pagination')} className={cn('my-6 flex justify-center', className)}>
      <ul className="flex flex-wrap items-center gap-1">
        <li>
          <button
            type="button"
            className={btn()}
            disabled={page <= 1}
            aria-label={t('previous_page')}
            onClick={() => onChange(page - 1)}
          >
            <ChevronLeft className="h-4 w-4" />
          </button>
        </li>
        {pageRange(page, totalPage).map((p, i) =>
          p === '…' ? (
            <li key={`e${i}`} className="px-2" aria-hidden>
              …
            </li>
          ) : (
            <li key={p}>
              <button
                type="button"
                className={btn(p === page)}
                aria-current={p === page ? 'page' : undefined}
                onClick={() => onChange(p)}
              >
                {p}
              </button>
            </li>
          )
        )}
        <li>
          <button
            type="button"
            className={btn()}
            disabled={page >= totalPage}
            aria-label={t('next_page')}
            onClick={() => onChange(page + 1)}
          >
            <ChevronRight className="h-4 w-4" />
          </button>
        </li>
      </ul>
    </nav>
  );
}
