import { Card, Skeleton } from '@mui/material';

export default function ProductItemCardSkeleton() {

  return (
    <Card className=" bg-white p-3 relative">
      <div className="w-full flex items-center justify-center relative">
        <Skeleton variant="rectangular" width={220} height={220} animation="wave" />
      </div>

      <div className=" bg-white">
        <h1>
          <Skeleton width={220} height={40} animation="wave" />
        </h1>

        <div className="flex justify-end">
          <Skeleton width={60} height={40} animation="wave" />
        </div>

        <div className="flex justify-between">
          <Skeleton width={220} height={40} animation="wave" />
        </div>

        <Skeleton width={240} height={40} animation="wave" />
      </div>
    </Card>
  );
}
