import { useState } from 'react';
import { useParams } from 'next/navigation';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { getProductByCategory } from '@/api/productApi';
import { PaginationParams } from '@/models/types/Request';
import ShowListProduct from '@/features/product/component/ShowListProduct';
import NotFound from './not-found';
import { Pagination } from '@mui/material';

export default function ProductByCategory() {
  const param = useParams();
  const { slug } = param;

  const [pagination, setPagination] = useState<PaginationParams>({
    page: 1,
    limit: 10,
    totalPage: 0,
  });

  const { data: listProduct, isLoading } = useQuery({
    queryKey: ['product', slug, pagination],
    queryFn: async () =>
      await getProductByCategory(slug, pagination.page - 1, pagination.limit).then((result) => {
        setPagination({ ...pagination, totalPage: result.data.totalPage });
        return result.data.responseList;
      }),
  });

  if (!listProduct || listProduct.length <= 0) return <NotFound />;

  return (
    <div className="w-[90%] mx-auto">
      <ShowListProduct listProduct={listProduct} loading={isLoading} />

      <Pagination
        count={pagination.totalPage}
        page={pagination.page}
        onChange={(event: React.ChangeEvent<any>, page: number) => {
          setPagination({ ...pagination, page: page });
        }}
      />
    </div>
  );
}
