'use client';
import { getReviewByProduct } from '@/api/reviewApi';
import { PaginationParams } from '@/models/types';
import { Review, ReviewStatistic } from '@/models/types/Review';
import { Pagination } from '@mui/material';
import { useQuery } from '@tanstack/react-query';
import React, { useState } from 'react';
import ReviewComponent from './ReviewComponent';

type PropsType = {
  productId: string;
  reviewStatictis: ReviewStatistic;
};

export default function ReviewProduct({ productId, reviewStatictis }: PropsType) {
  const [filter, setFilter] = useState({ rating: 0, hasMedia: false });
  const [pagination, setPagination] = useState<PaginationParams>({ page: 1, limit: 10, totalPage: 0 });

  const { data: reviews } = useQuery<Review[]>({
    queryKey: ['review', filter, pagination],
    queryFn: async () =>
      await getReviewByProduct(productId, filter, pagination).then((response) => {
        setPagination({ ...pagination, totalPage: response.data.paginationInfo.totalPage });
        return response.data.responseList;
      }),
  });

  if (!reviews) return <div>Loading</div>;

  const filterValue = [
    { name: 'All', value: 0 },
    { name: '5 sao', value: 5 },
    { name: '4 sao', value: 4 },
    { name: '3 sao', value: 3 },
    { name: '2 sao', value: 2 },
    { name: '1 sao', value: 1 },
  ];

  return (
    <React.Fragment>
      <div className="flex my-10">
        <div className="w-1/5">{reviewStatictis.averageRating}/5 sao</div>
        <div className="space-x-2">
          {filterValue.map((item) => (
            <span
              key={item.value}
              className={`border-2 rounded hover:cursor-pointer px-4 py-2 ${item.value === filter.rating ? 'border-green-400' : 'border-gray-400'}`}
              onClick={() => setFilter({ ...filter, rating: item.value })}
            >
              {item.name} ({reviewStatictis.reviewRating5Star})
            </span>
          ))}
          <span
            className={`border-2 rounded hover:cursor-pointer px-4 py-2 ${filter.hasMedia ? 'border-green-400' : 'border-gray-400'}`}
            onClick={() => setFilter({ ...filter, hasMedia: !filter.hasMedia })}
          >
            Có hình ảnh ({reviewStatictis.reviewWithMedia})
          </span>
        </div>
      </div>
      {reviews.map((review) => (
        <ReviewComponent key={review.id} review={review} />
      ))}
      {pagination.totalPage && pagination.totalPage > 1 && (
        <Pagination
          shape="rounded"
          page={pagination.page}
          count={pagination.totalPage}
          onChange={(_, page) => setPagination({ ...pagination, page: page })}
        />
      )}
    </React.Fragment>
  );
}
