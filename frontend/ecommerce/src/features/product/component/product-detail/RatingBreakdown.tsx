'use client';

import StarRating from '@/components/common/StarRating';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui';
import type { RatingSummary } from '@/graphql/types';
import { formatNumber } from '@/utils/helper';
import { ChevronDown } from 'lucide-react';
import { useTranslations } from 'next-intl';

export default function RatingBreakdown({ rating }: { rating: RatingSummary }) {
  const t = useTranslations();
  const average = Math.round(rating.average * 10) / 10;
  const rows = [5, 4, 3, 2, 1].map((star) => {
    const count = rating.stars[star - 1] ?? 0;
    return { star, percent: rating.count ? Math.round((count / rating.count) * 100) : 0 };
  });

  return (
    <div className="flex flex-wrap items-center gap-2">
      <Popover>
        <PopoverTrigger asChild>
          <button type="button" className="flex items-center gap-1" aria-label={t('product.rating_breakdown')}>
            <span>{average}</span>
            <StarRating value={average} />
            <ChevronDown size={16} aria-hidden />
          </button>
        </PopoverTrigger>
        <PopoverContent className="w-80">
          <div className="flex items-center gap-2">
            <StarRating value={average} />
            <span>{t('product.out_of_5', { value: average })}</span>
          </div>
          <p className="text-sm mb-2">{t('product.ratings_count', { count: rating.count })}</p>
          <ul className="space-y-1">
            {rows.map(({ star, percent }) => (
              <li key={star} className="flex items-center gap-2 text-sm">
                <span className="w-14 shrink-0">{t('product.stars', { count: star })}</span>
                <span className="flex-1 h-3 bg-gray-200 rounded" aria-hidden>
                  <span className="block h-full bg-yellow-400 rounded" style={{ width: `${percent}%` }} />
                </span>
                <span className="w-10 text-right">{percent}%</span>
              </li>
            ))}
          </ul>
        </PopoverContent>
      </Popover>
      <span className="opacity-80" aria-hidden>
        |
      </span>
      <span>
        {formatNumber(rating.count)} {t('product.rating')}
      </span>
    </div>
  );
}
