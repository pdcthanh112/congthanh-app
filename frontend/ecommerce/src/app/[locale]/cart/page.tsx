import ShowListCart from '@/features/cart/component/ShowListCart';
import { getCartByCustomerId } from '@/api/cartApi';
import Link from 'next/link';
import { getServerSession } from 'next-auth/next';
import Image from 'next/image';
import { IMAGE, PATH } from '@/utils/constants/path';
import { Button } from '@/components/ui';
import { getTranslations } from 'next-intl/server';

async function getCartData(customerId: string) {
  return await getCartByCustomerId(customerId).then(response => response.data);
}

export default async function CartPage() {
  const session  = await getServerSession();
  const t = await getTranslations()

  if (!session || !session.user) {
    return (
      <div className="w-full p-4">
        <div className="p-4 text-center bg-yellow-100 rounded-md">
          <h3 className="text-lg font-medium">Vui lòng đăng nhập để xem giỏ hàng</h3>
          <Link href="/login?callbackUrl=/cart" className="mt-2 text-blue-600 hover:underline">
            Đăng nhập ngay
          </Link>
        </div>
      </div>
    );
  }

  const data = await getCartData("6304f010-3985-4ecc-a139-e8aba1eee7b1");

    if (!data) {
    return (
      <div className="w-full h-[40rem] bg-white text-center">
        <div className="w-full pt-12 flex justify-center">
          <span className="w-20 h-20 relative">
            <Image src={IMAGE.cartEmpty} alt={'Cart Empty'} objectFit="fit" fill />
          </span>
        </div>
        <div className="w-full flex justify-center font-medium text-xl mb-5">{t('cart.cart_empty')}</div>
        <div className="w-full flex justify-center">
          <Link href={PATH.HOME}>
            <Button className="bg-green-400 rounded-xl text-white">{t('common.back_to_home')}</Button>
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="w-4/5 mx-auto">
      <h3 className="px-5 py-2 mb-3 text-3xl bg-white">{'cart.your_cart'}</h3>
      <ShowListCart data={data} loading={false} />
    </div>
  );
}
