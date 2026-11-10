import { Skeleton } from '@mui/material';

export default function ProductDetailSkeleton() {
  return (
    <div className="w-[80%] mx-auto">
      <div className="bg-white mt-3 flex">
        <div className="w-[40%] justify-center flex p-3">
          <Skeleton variant="rectangular" width={300} height={500} />
        </div>
        <div className="w-[60%] p-3">
          <Skeleton variant="text" width={400} height={50} animation="wave" />
          <Skeleton height={300} width={550} />
        </div>
      </div>

      <div className="bg-white mt-10 px-5">
        <Skeleton height={200} />
      </div>

      <div className="bg-white mt-10 px-5">
        <Skeleton height={300} />
      </div>
    </div>
  );
}
