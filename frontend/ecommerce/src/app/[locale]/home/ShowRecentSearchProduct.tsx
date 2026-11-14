'use client';

import React, { useState } from 'react';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import PaginationComponent from '@/components/common/PaginationComponent';
import ShowListProduct from '@/features/product/component/ShowListProduct';
import { homeProductsQuery } from './queries';

const ShowRecentSearchProduct = () => {
  const [page, setPage] = useState(1);
  const { data, isPending } = useQuery({ ...homeProductsQuery(page), placeholderData: keepPreviousData });

  return (
    <React.Fragment>
      <ShowListProduct listProduct={data?.responseList} loading={isPending} />
      <PaginationComponent
        className="justify-end"
        page={page}
        totalPage={data?.paginationInfo.totalPage ?? 0}
        onChange={setPage}
      />
    </React.Fragment>
  );
};

export default ShowRecentSearchProduct;
