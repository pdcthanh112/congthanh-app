import React, { useState } from 'react';
import { PaginationParams } from '@/models/types/Request';
import { useQuery } from '@tanstack/react-query';
import { getOrderByStatus } from '@/api/orderApi';
import { Order } from '@/models/types/OrderModel';
import ListEmpty from '../../assets/images/list-empty.png';
import Image from 'next/image';
import { Pagination } from '@mui/material';

export default function OrderPage() {
  const [status, setStatus] = useState('ALL');
  const [pagination, setPagination] = useState<PaginationParams>({ page: 1, limit: 10, totalPage: 0 });

  const { data: listOrder, isLoading } = useQuery({
    queryKey: ['order', pagination, status],
    queryFn: async () =>
      await getOrderByStatus(status, pagination.page - 1, pagination.limit).then((result) => {
        setPagination({ ...pagination, totalPage: result.data.totalPage });
        return result.data.responseList;
      }),
  });

  const items = [
    { key: 'ALL', label: 'All' },
    { key: 'PENDING', label: 'Pending' },
    { key: 'PROCESSING', label: 'Processing' },
    { key: 'SHIPPED', label: 'Shipped' },
    { key: 'DELIVERED', label: 'Delivered' },
    { key: 'CANCEL', label: 'Cancel' },
    { key: 'RETURNED', label: 'Returned' },
    { key: 'COMPLETED', label: 'Completed' },
  ];

  console.log('TTTTTTTTTTTTTTTT', listOrder);

  return (
    <div className="bg-white w-4/5 mx-auto p-2">
      <div className="flex my-3 justify-around">
        {items.map((item) => (
          <span
            key={item.key}
            className={`px-5 py-1 hover:cursor-pointer ${status === item.key ? 'border-b-2 border-yellow-300' : ''}`}
            onClick={() => setStatus(item.key)}
          >
            {item.label}
          </span>
        ))}
      </div>

      <div className="min-h-[30rem]">
        {isLoading ? (
          <div>Loading</div>
        ) : (
          <>
            {listOrder ? (
              <>
                {listOrder.map((item: Order) => (
                  <div key={item.id}>{item.customer}afafafasdfafas</div>
                ))}
                <Pagination
                  count={pagination.totalPage}
                  page={pagination.page}
                  onChange={(_, page: number) => {
                    setPagination({ ...pagination, page: page });
                  }}
                />
              </>
            ) : (
              <div className="pt-40">
                <Image src={ListEmpty} alt={''} width={100} className="m-auto" />
                <span className="flex justify-center my-3">List empty</span>
              </div>
            )}
          </>
        )}
      </div>
    </div>
  );
}
