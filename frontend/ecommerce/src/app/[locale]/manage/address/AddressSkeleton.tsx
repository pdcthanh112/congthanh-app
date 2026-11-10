import React from 'react';
import { Skeleton } from '@mui/material';

const AddressSkeleton = () => {
  return (
    <div className="my-2 px-5 py-2 border-t-2 grid grid-cols-12">
      <div className="col-span-10">
        <span className="mr-1 flex">
          <Skeleton width={200} height={50} sx={{ marginRight: 1 }} />
          <Skeleton width={200} height={50} />
        </span>

        <Skeleton width={'80%'} height={50} />
      </div>
      <div className="col-span-2">
        <Skeleton height={50} />
        <div className="flex justify-end">
          <Skeleton width={60} height={40} sx={{ marginRight: 1 }} />
          <Skeleton width={60} height={40} />
        </div>
      </div>
    </div>
  );
};

export default AddressSkeleton;
