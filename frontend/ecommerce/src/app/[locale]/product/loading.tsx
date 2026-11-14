import { Skeleton } from '@/components/ui';
import ShowListProduct from '@/features/product/component/ShowListProduct';

export default function ProductListLoading() {
  return (
    <div className="mx-auto mt-5 grid w-full max-w-7xl grid-cols-1 gap-6 px-4 lg:grid-cols-12">
      <div className="hidden space-y-4 lg:col-span-3 lg:block">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-8 w-full" />
        ))}
      </div>
      <div className="lg:col-span-9">
        <ShowListProduct loading />
      </div>
    </div>
  );
}
