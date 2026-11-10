import Link from 'next/link';
import { PATH } from '@/utils/constants/path';

export default function ThankYouPage() {
  return (
    <div className="w-screen h-[38rem] bg-white flex justify-center items-center">
      <div className="w-1/2 h-1/2 text-center">
        <p className="text-2xl">Your checkout have been successfully</p>
        <p>
          You can tracking your order&nbsp;
          <Link href={'/order'} className="hover:cursor-pointer hover:underline">
            here
          </Link>
        </p>
        <Link href={PATH.HOME} className="hover:cursor-pointer hover:underline">
          Home
        </Link>
      </div>
    </div>
  );
}
