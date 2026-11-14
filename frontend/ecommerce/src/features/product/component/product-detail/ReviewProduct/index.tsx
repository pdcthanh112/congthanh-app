'use client';

import React, { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useTranslations } from 'next-intl';
import { getReviewByProduct } from '@/api/reviewApi';
import PaginationComponent from '@/components/common/PaginationComponent';
import { Skeleton } from '@/components/ui';
import { ReviewStatistic } from '@/models/types/Review';
import { REVIEW_KEY } from '@/utils/constants/queryKey';
import ReviewComponent from './ReviewComponent';

type PropsType = {
  productId: string;
  reviewStatictis: ReviewStatistic;
};

const PAGE_SIZE = 10;

export default function ReviewProduct({ productId, reviewStatictis }: PropsType) {
  const t = useTranslations();
  const [filter, setFilter] = useState({ rating: 0, hasMedia: false });
  const [page, setPage] = useState(1);

  const { data, isPending } = useQuery({
    queryKey: [REVIEW_KEY, productId, filter, page],
    queryFn: async () => (await getReviewByProduct(productId, filter, { page, limit: PAGE_SIZE })).data,
    placeholderData: keepPreviousData,
  });

  const starCount: Record<number, number> = {
    5: reviewStatictis.reviewRating5Star,
    4: reviewStatictis.reviewRating4Star,
    3: reviewStatictis.reviewRating3Star,
    2: reviewStatictis.reviewRating2Star,
    1: reviewStatictis.reviewRating1Star,
  };

  const changeFilter = (next: Partial<typeof filter>) => {
    setFilter((f) => ({ ...f, ...next }));
    setPage(1);
  };

  const chip = (active: boolean) =>
    `rounded border-2 px-3 py-1.5 ${active ? 'border-green-500' : 'border-gray-300 hover:border-gray-400'}`;

  return (
    <React.Fragment>
      <div className="my-8 flex flex-col gap-4 md:flex-row">
        <div className="md:w-1/5 text-lg font-medium">
          {t('product.out_of_5', { value: reviewStatictis.averageRating })}
        </div>
        <div className="flex flex-wrap gap-2">
          <button type="button" className={chip(filter.rating === 0)} onClick={() => changeFilter({ rating: 0 })}>
            {t('product.all')} ({reviewStatictis.totalReview})
          </button>
          {[5, 4, 3, 2, 1].map((star) => (
            <button
              key={star}
              type="button"
              aria-pressed={filter.rating === star}
              className={chip(filter.rating === star)}
              onClick={() => changeFilter({ rating: filter.rating === star ? 0 : star })}
            >
              {t('product.star_count', { count: star })} ({starCount[star]})
            </button>
          ))}
          <button
            type="button"
            aria-pressed={filter.hasMedia}
            className={chip(filter.hasMedia)}
            onClick={() => changeFilter({ hasMedia: !filter.hasMedia })}
          >
            {t('product.with_media')} ({reviewStatictis.reviewWithMedia})
          </button>
        </div>
      </div>

      {isPending ? (
        <div className="space-y-4">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-20 w-full" />
          ))}
        </div>
      ) : !data?.responseList.length ? (
        <p className="py-6 text-center text-muted-foreground">{t('product.no_review')}</p>
      ) : (
        data.responseList.map((review) => <ReviewComponent key={review.id} review={review} />)
      )}

      {data && data.paginationInfo.totalPage > 1 && (
        <PaginationComponent page={page} totalPage={data.paginationInfo.totalPage} onChange={setPage} />
      )}
    </React.Fragment>
  );
}
