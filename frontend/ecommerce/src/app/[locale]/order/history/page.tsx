import React, { useState } from 'react';
import { getOrderByStatus } from '@/api/orderApi';
import { Customer } from '@/models/types/CustomerModel';
import { useAppSelector } from '@/redux/store';
import { useQuery } from '@tanstack/react-query';
import { OrderItem, PaginationParams } from '@/models/types';


export default function History(): React.ReactElement {
  const currentUser: Customer = useAppSelector((state) => state.auth.currentUser);
  const [status, setStatus] = useState('ALL');
  const [pagination, setPagination] = useState<PaginationParams>({ page: 1, limit: 10, totalPage: 0 });

  const { data: listOrder, isLoading } = useQuery({
    queryKey: ['history', status, pagination],
    queryFn: async () =>
      await getOrderByStatus(status, pagination.page - 1, pagination.limit).then((response) => {
        if (response && response.data) {
          setPagination({ ...pagination, totalPage: response.data.totalPage });
          return response.data.responseList;
        }
      }),
});
  console.log('CCCCCCCCCCCCCCCcc', listOrder);
  const statusData = [
    { label: 'All', value: 'ALL' },
    { label: 'Waiting to Pay', value: 'WAITING_TO_PAY' },
    { label: 'Shipping', value: 'SHIPPING' },
    { label: 'Receiving', value: 'RECEIVING' },
    { label: 'Completed', value: 'COMPLETED' },
    { label: 'Canceled', value: 'CANCELED' },
    { label: 'Return/Refund', value: 'RETURN/REFUND' },
  ];

  return (
    <div className="mx-auto my-3 w-3/5">
      <menu className="flex bg-white h-fit px-4 py-3 justify-center">
        {statusData.map((item) => (
          <li key={item.value} className={`px-5 py-2 hover:cursor-pointer ${item.value === status && 'border-b-2 border-yellow-400'}`} onClick={() => setStatus(item.value)}>
            {item.label}
          </li>
        ))}
      </menu>

      {isLoading ? (
        <div>Loading</div>
      ) : (
        <React.Fragment>
          {/* {listOrder ? (
            listOrder.map((item: OrderDetail) => (
              <div key={item.id} className="bg-white my-2 px-3 py-2">
                <div className="flex justify-end">
                
                  {moment.unix(item.).format("MM/DD/YYYY")}
                  {item.status}
                  </div>
                <div className='bg-yellow-200'>adfs</div>
                <div className='flex justify-end'>Total: {item.total}</div>
              </div>
            ))
          ) : (
            <div>null</div>
          )} */}
        </React.Fragment>
      )}
    </div>
  );
}
