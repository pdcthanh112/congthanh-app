import { Skeleton } from '@/components/ui';

export default function ProductDetailSkeleton() {
  return (
    <div className="w-full max-w-6xl mx-auto px-4 my-3">
      <div className="bg-white flex flex-col md:flex-row gap-5 px-3 py-4">
        <Skeleton className="w-full md:w-2/5 aspect-square" />
        <div className="flex-1 space-y-4">
          <Skeleton className="h-10 w-4/5" />
          <Skeleton className="h-6 w-2/5" />
          <Skeleton className="h-10 w-1/3" />
          <Skeleton className="h-24 w-full" />
          <Skeleton className="h-10 w-1/2" />
        </div>
      </div>
    </div>
  );
}
