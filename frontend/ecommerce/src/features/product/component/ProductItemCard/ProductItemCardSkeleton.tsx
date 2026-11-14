import { Card, Skeleton } from '@/components/ui';

export default function ProductItemCardSkeleton() {
  return (
    <Card className="flex flex-col gap-3 p-3" aria-hidden>
      <Skeleton className="aspect-square w-full" />
      <Skeleton className="h-5 w-full" />
      <Skeleton className="ml-auto h-6 w-1/3" />
      <Skeleton className="h-4 w-full" />
      <Skeleton className="h-9 w-full" />
    </Card>
  );
}
