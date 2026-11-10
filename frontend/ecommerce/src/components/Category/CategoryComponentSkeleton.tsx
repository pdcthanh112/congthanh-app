import React from 'react';
import { Skeleton } from '@/components/ui/skeleton';

export default function CategoryComponentSkeleton() {

  return (
    <div className="flex flex-row h-[30rem]">
      <div className="flex w-1/4">
        <ul className="list-none w-full h-full border-r-2 border-gray-300">
          {Array.from({length: 6}).map((_, id) => (
            <li key={id} className="hover:bg-gray-100 p-3">
              <Skeleton className="h-10 w-64 rounded-full" />
            </li>
          ))}
        </ul>
      </div>

      <div className="grid grid-cols-4 gap-3 p-3 w-3/4">
        {Array.from({length: 7}).map((_, id) => (
          <div key={id} className="flex flex-col">
            <Skeleton className="h-6 w-40 rounded-full  mb-2" />
            {Array.from({length: 5}).map((_) => (
              <Skeleton className="h-6 w-40 rounded-full" />
            ))}
          </div>
        ))}
      </div>
    </div>
  );
}
