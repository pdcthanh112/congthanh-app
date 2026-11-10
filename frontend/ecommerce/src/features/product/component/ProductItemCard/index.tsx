import Image from 'next/image';
import DefaultImage from '@/assets/images/default-image.jpg';
import { Card, Rating, Icon, Box } from '@mui/material';
import { ShoppingCart, Source } from '@mui/icons-material';
import { useAppSelector } from '@/redux/store';
import { useQuery } from '@tanstack/react-query';
import { toast } from 'react-toastify';
import { HeartEmpty, HeartFull } from '@/assets/icons';
import { useAddProductToWishlist, useRemoveProductFromWishlist } from '@/hooks/wishlist/wishlistHook';
import { getDefaultImageByProductId, getSoldByProduct } from '@/api/productApi';
import { Customer, Product, Wishlist } from '@/models/types';
import { getWishlistByCustomer } from '@/api/wishlistApi';
import { WISHLIST_KEY } from '@/utils/constants/queryKey';
import { getStatisticFromProduct } from '@/api/reviewApi';
import { Button } from '@/components/ui';
import { formatCurrency, formatNumber } from '@/utils/helper';
import Link from 'next/link';
import { useTranslations } from 'next-intl';
import { PATH } from '@/utils/constants/path';
import useAppModalStore from '@/store/useAppModal';
import { Heart } from 'lucide-react';

type ProductItemProps = {
  product: Product;
};

const ProductItemCard = ({ product }: ProductItemProps) => {
  const currentUser: Customer = useAppSelector((state) => state.auth.currentUser);
  const t = useTranslations();
  const { openModalAuth } = useAppModalStore();

  const { data: reviewStatistic } = useQuery({
    queryKey: ['review', product],
    queryFn: async () => await getStatisticFromProduct(product.id).then((response) => response.data),
  });

  const { data: soldProduct } = useQuery({
    queryKey: ['product-sold', product],
    queryFn: async () => await getSoldByProduct(product.id).then((response) => response.data),
  });

  const { data: wishlist } = useQuery({
    queryKey: [WISHLIST_KEY],
    queryFn: async () => await getWishlistByCustomer(currentUser.userInfo.accountId).then((response) => response.data),
  });

  const { mutate: addProductToWishlist } = useAddProductToWishlist();
  const { mutate: removeProductFromWishlist } = useRemoveProductFromWishlist();

  const handleAddToCart = (productId: string) => {
    if (currentUser) {
      //  setshowSelectCart(true)
      // addToCart('CART_ID', 1, productId);
    } else {
      openModalAuth();
    }
  };

  const handleAddToWishlist = (productId: string) => {
    if (currentUser) {
      try {
        addProductToWishlist(
          { customerId: currentUser.userInfo.accountId, productId: productId },
          {
            onSuccess() {
              toast.success(t('wishlist.add_item_to_wishlist_successfully'));
            },
            onError(error) {
              toast.error(t('wishlist.add_item_to_wishlist_failed'));
              console.log(error);
            },
          }
        );
      } catch (error) {
        toast.error(t('wishlist.add_item_to_wishlist_failed'));
      }
    } else {
      openModalAuth();
    }
  };

  const handleRemoveFromWishlist = async (productId: string) => {
    if (currentUser) {
      try {
        removeProductFromWishlist(
          { customerId: currentUser.userInfo.accountId, productId: productId },
          {
            onSuccess() {
              toast.success(t('wishlist.remove_item_from_wishlist_successfully'));
            },
            onError(error) {
              toast.error(t('wishlist.remove_item_from_wishlist_failed'));
              console.log(error);
            },
          }
        );
      } catch (error) {
        toast.error(t('wishlist.remove_item_from_wishlist_failed'));
      }
    } else {
      openModalAuth();
    }
  };

  return (
    <Card key={product.id} className=" bg-white z-30 p-3 text-sm hover:cursor-pointer">
      <div className="w-full flex items-center justify-center relative group">
        <Box width={220} height={220} position={'relative'}>
          <Image src={product.thumbnail.imagePath || DefaultImage} alt={product.name} fill objectFit="fill" />
        </Box>
        {/* <ul className="w-full h-36 bg-gray-100 absolute -bottom-36 flex flex-col items-end justify-center gap-2 font-semibold px-2 border-l border-r group-hover:bottom-0 duration-700">
          <li
            className="text-gray-600 hover:text-black text-sm font-medium border-b-[1px] border-b-gray-400 hover:border-b-gray-700 flex items-center justify-end gap-2 hover:cursor-pointer duration-300 w-full"
            title={t('common.add_to_cart')}
            onClick={() => handleAddToCart(product.id)}
          >
            {t('common.add_to_cart')}
            <Icon component={ShoppingCart} />
          </li>
          <Link href={PATH.PRODUCT_PATH_URL.PRODUCT_DETAIL + `${product.slug}`}>
            <li
              className="text-gray-600 hover:text-black text-sm font-medium border-b-[1px] border-b-gray-400 hover:border-b-gray-700 flex items-center justify-end gap-2 hover:cursor-pointer duration-300 w-full"
              title={t('common.view_detail')}
            >
              {t('common.view_detail')}
              <Icon component={Source} />
            </li>
          </Link>
          {wishlist?.product.find((item: Wishlist) => item.id === product.id) === undefined ? (
            <li
              className="text-gray-600 hover:text-black text-sm font-medium border-b-[1px] border-b-gray-400 hover:border-b-gray-700 flex items-center justify-end gap-2 hover:cursor-pointer duration-300 w-full"
              title={t('common.add_to_wishlist')}
              onClick={() => handleAddToWishlist(product.id)}
            >
              {t('common.add_to_wishlist')}
              <Icon component={HeartEmpty} />
            </li>
          ) : (
            <li
              className="text-gray-600 hover:text-black text-sm font-medium border-b-[1px] border-b-gray-400 hover:border-b-gray-700 flex items-center justify-end gap-2 hover:cursor-pointer duration-300 w-full"
              title={t('common.remove_from_wishlist')}
              onClick={() => handleRemoveFromWishlist(product.id)}
            >
              {t('common.remove_from_wishlist')}
              <Icon component={HeartFull} />
            </li>
          )}
        </ul> */}
      </div>

      <div className="relative z-50 bg-white">
        <h1 className="truncate opacity-70 hover:opacity-100 my-2" title={product.name}>
          {product.name}
        </h1>

        <div className="mb-5 flex justify-end font-medium text-yellow-500 text-lg">
          <span>{formatCurrency(10000000, 'vi', 'VND')}</span>
        </div>

        <div className="flex justify-between">
          <span className="flex items-center">
            <Rating precision={0.1} value={reviewStatistic?.averageRating} size="small" readOnly />
            <span className="ml-1">{reviewStatistic?.averageRating.toFixed(1)}</span>
          </span>

          <span className="mr-1">
            {formatNumber(Number(reviewStatistic?.totalReview))} {t('product.rating')}
          </span>
        </div>

        <span className="italic ml-2">
          {t('product.Sold')}: {soldProduct}
        </span>

        <div className="flex justify-between">
          <Button className="w-[calc(100% - 8px)] bg-yellow-400" onClick={() => handleAddToCart(product.id)}>
            {t('common.add_to_cart')}
          </Button>
          <Heart className="w-8 h-8" color="#e0281b" fill='#e0281b'/>
        </div>
      </div>
    </Card>
  );
};

export default ProductItemCard;
