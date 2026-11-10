import { useEffect, useState } from 'react';
import Image from 'next/image';
import { CartItem, ProductImage } from '@/models/types';
import { useRouter } from 'next/navigation';
import DefaultImage from '@/assets/images/default-image.jpg';

type PropsType = {
  item: CartItem;
};

const ShowCartItem = ({ item }: PropsType) => {
  const router = useRouter();

  return (
    <div
      key={item.id}
      className="flex justify-between px-5 py-2 hover:bg-gray-100 hover:cursor-pointer"
      title={item.product.name}
      onClick={() => router.push(`product/${item.product.id}`)}>
      <span className="flex items-center">
        <Image src={item.product.thumbnail.imagePath || DefaultImage} alt={item.product.name ?? ""} width={40} height={40} className="border border-gray-300" />
        <h4 className="truncate ml-2 w-56 text-sm">{item.product.name}</h4>
      </span>
      <span className="text-yellow-500">{item.product.price?.formatted}</span>
    </div>
  );
};

export default ShowCartItem;
