'use client';
import React, { useState } from 'react';
import { getAllProduct } from '@/api/productApi';
import ShowListProduct from '@/features/product/component/ShowListProduct';
import { PaginationParams, Product } from '@/models/types';
import { PRODUCT_KEY } from '@/utils/constants/queryKey';
import { Pagination } from '@mui/material';
import { useQuery } from '@tanstack/react-query';

const ShowRecentSearchProduct = () => {

  const [pagination, setPagination] = useState<PaginationParams>({ page: 1, limit: 10, totalPage: 0 });

  const { data: listProduct, isLoading } = useQuery({
    queryKey: [PRODUCT_KEY, pagination],
    queryFn: async () =>
      await getAllProduct(pagination.page, pagination.limit).then((response) => {
        setPagination({ ...pagination, totalPage: response.data.paginationInfo.totalPage });
        return response.data.responseList;
      }),
  });

  return (
    <React.Fragment>
      <ShowListProduct listProduct={listProduct as Product[]} loading={isLoading} />
      <Pagination
        sx={{ display: 'flex', justifyContent: 'end', mt: 3 }}
        page={pagination.page}
        count={pagination.totalPage}
        shape="rounded"
        onChange={(_event, page) => {
          setPagination({ ...pagination, page: page });
        }}
      />
    </React.Fragment>
  );
};

export default ShowRecentSearchProduct;
