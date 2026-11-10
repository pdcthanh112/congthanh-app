'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { Rating, Icon, Avatar, TableContainer, Table, TableBody, TableRow, TableCell, Popover } from '@mui/material';
import { Storefront, ForumOutlined, KeyboardArrowDown } from '@mui/icons-material';
import Image from 'next/image';
import DefaultImage from '@/assets/images/default-image.jpg';
import { formatCurrency, formatNumber } from '@/utils/helper';
import { useTranslations } from 'next-intl';
import { useAddProductToWishlist, useRemoveProductFromWishlist } from '@/hooks/wishlist/wishlistHook';
import { AddToCartIcon, HeartEmpty, HeartFull } from '@/assets/icons';
import { toast } from 'react-toastify';
import { useAddProductToCart } from '@/features/cart/hooks';
import {
  Product,
  ProductAttribute,
  ProductImage,
  ProductOptions,
  ProductOptionValueDisplay,
  ProductVariant,
} from '@/models/types';
import QuantitySelector from '@/components/quantity-selector/QuantitySelector';
import { PRODUCT_KEY, WISHLIST_KEY } from '@/utils/constants/queryKey';
import ProductDetailSkeleton from '../ProductDetailSkeleton';
import { ReviewStatistic } from '@/models/types/Review';
import { BarChart, Bar, XAxis, ResponsiveContainer } from 'recharts';
import { Button } from '@/components/ui/button';
import { PATH } from '@/utils/constants/path';
import { useSession } from 'next-auth/react';
import useAppModalStore from '@/store/useAppModal';
import { useWishlistStore } from '@/store/wishlistStore';
import BreadcrumbComponent from '@/components/breadcrumb/Breadcrumb';

type ProductDetailProps = {
  product: Product;
  pvid: string | null;
  reviewStatistic: ReviewStatistic;
  productOption?: ProductOptions[];
  productVariant?: ProductVariant[];
  productOptionValueGet?: ProductOptionValueDisplay[];
};

type CurrentSelectedOption = {
  [key: string]: string;
};

export default function ProductDetail({
  product,
  pvid,
  reviewStatistic,
  productOption,
  productVariant,
  productOptionValueGet,
}: ProductDetailProps) {
  const { data: user } = useSession();
  const router = useRouter();
  console.log('VVVVVVVVVVVVVVVVVVVVVVVVVv', productVariant);
  const t = useTranslations();

  const [quantity, setQuantity] = useState(1);
  const [sold, setSold] = useState<number>(0);

  // const [showReviewStatistic, setShowReviewStatistic] = useState<boolean>(false);
  const [currentImage, setCurrentImage] = useState<ProductImage>();
  const [listImages, setListImages] = useState<string[]>([]);
  const [currentIndexImages, setCurrentIndexImages] = useState([0, 5]);
  const [anchorEl, setAnchorEl] = React.useState<HTMLDivElement | null>(null);

  const { openModalAuth } = useAppModalStore((state) => state);

  const imageRef = useRef<HTMLImageElement>(null);

  const { mutate: addProductToCart } = useAddProductToCart();

  const { mutate: addProductToWishlist } = useAddProductToWishlist();
  const { mutate: removeProductFromWishlist } = useRemoveProductFromWishlist();

  const initCurrentSelectedOption = useMemo(() => {
    if (!productOption?.length || !productVariant?.length) {
      setListImages([
        ...(product.thumbnail.imagePath ? [product.thumbnail.imagePath] : []),
        // ...product.thumbnail.imagePath,
        ...(Array.isArray(product.thumbnail.imagePath) ? product.thumbnail.imagePath : []),
      ]);
      return {};
    }

    const productVariation = pvid && productVariant.find((item) => item.id.toString() === pvid);
    if (productVariation) {
      return productVariation.options;
    }

    setListImages([
      ...(product.thumbnail.imagePath ? [product.thumbnail.imagePath] : []),
      // ...product.thumbnail.imagePath,
      ...(Array.isArray(product.thumbnail.imagePath) ? product.thumbnail.imagePath : []),
    ]);
    return productVariant[0].options;
  }, [productOption, productVariant, pvid]);

  const [currentSelectedOption, setCurrentSelectedOption] = useState<CurrentSelectedOption>(initCurrentSelectedOption);
  console.log('YYYYYYYYYYYYYYYYYYYYYYYYYYY', currentSelectedOption);
  const [optionSelected, setOptionSelected] = useState<CurrentSelectedOption>({});
  const [isUnchecking, setIsUnchecking] = useState<boolean>(false);
  const [currentProduct, setCurrentProduct] = useState<Product | ProductVariant>(product);

  // useEffect(() => {
  //   if (productOption && productOption.length > 0 && productVariant && productVariant.length > 0) {
  //     // router.query.pvid = currentProduct.id.toString();
  //     // router.push(router, undefined, { shallow: true });
  //     const url = new URL(window.location.href);
  //     url.searchParams.set('pvid', currentProduct.id.toString());
  //     router.replace(url.toString());
  //   }

  // }, [productOption, productVariant, pvid, currentProduct.id]);

  useEffect(() => {
    const isOptionSelected = (key: string, currentSelectedOption: CurrentSelectedOption, item: ProductVariant) => {
      return currentSelectedOption[+key] === item.options[+key];
    };

    const areAllOptionsSelected = (
      optionKeys: string[],
      currentSelectedOption: CurrentSelectedOption,
      item: ProductVariant
    ) => {
      return optionKeys.every((key: string) => isOptionSelected(key, currentSelectedOption, item));
    };

    const findProductVariationMatchAllOptions = () => {
      return productVariant?.find((item) => {
        const optionKeys = Object.keys(item.options);
        console.log('KKKKKKKKKKKKKKKKKKKK', optionKeys);
        return (
          optionKeys.length === Object.keys(currentSelectedOption).length &&
          areAllOptionsSelected(optionKeys, currentSelectedOption, item)
        );
      });
    };

    const updateListImagesByProductVariationMatchAllOptions = (variation: ProductVariant) => {
      const urls = [
        ...(variation.thumbnail?.imagePath ? [variation.thumbnail.imagePath] : []),
        ...variation.image.map((image) => image.imagePath),
      ];
      setListImages(urls);
      setCurrentProduct(variation);
      setCurrentSelectedOption(variation.options);
    };

    const updateListImagesBySelectedOption = (productVariations: ProductVariant[]) => {
      const productSelected = productVariations.find((item) => {
        return item.options[+Object.keys(optionSelected)[0]] == Object.values(optionSelected)[0];
      });

      if (productSelected) {
        const urlList = productSelected.image.map((image) => image.imagePath);
        setListImages([
          ...(productSelected.thumbnail?.imagePath ? [productSelected.thumbnail.imagePath] : []),
          ...urlList,
        ]);
        setCurrentSelectedOption(productSelected.options);
        setCurrentProduct(productSelected);
      }
    };

    if (productOption?.length && productVariant?.length) {
      const productVariationMatchAllOptions = findProductVariationMatchAllOptions();
      console.log('FFFFFFFFFFFFFFFFFFFFFFFFF', productVariationMatchAllOptions);
      if (productVariationMatchAllOptions) {
        updateListImagesByProductVariationMatchAllOptions(productVariationMatchAllOptions);
      } else if (!isUnchecking) {
        updateListImagesBySelectedOption(productVariant);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
    // }, [JSON.stringify(currentSelectedOption)]);
  }, [currentSelectedOption]);

  //   const { data: product, isLoading } = useQuery({
  //     queryKey: [PRODUCT_KEY, productSlug],
  //     queryFn: async () => await getProductBySlug(productSlug as string).then(async(result) => {
  //           await getRatingStarofProduct(result.id).then((response) => {
  //             if (response && response.data) {
  //               setRatingStar(response.data);
  //             }
  //           });
  //           await getStoreById(result.store).then((response) => {
  //             if (response && response.data) {
  //               setStore(response.data);
  //             }
  //           });
  //           await getAttributeByProductId(result.id).then((response) => {
  //             if (response && response.data) {
  //               setProductAttribute(response.data);
  //             }
  //           });
  //           await getImageByProductId(result.id).then((response) => {
  //             if (response && response.data) {
  //               setProductImage(response.data);
  //               setCurrentImage(response.data.find((image: ProductImage) => image.isDefault === true));
  //             }
  //           });
  //           await getSoldByProduct(result.id).then((response) => {
  //             if (response && response.data) {
  //               setSold(response.data);
  //             }
  //           });
  //           return result.data
  //         },
  //    )
  // });

  const { checkExistItemInWishlist, addToWishlist, removeFromWishlist } = useWishlistStore();

  const handleAddProductToCart = () => {
    if (user) {
      try {
        addProductToCart(
          { productId: product.id, quantity: quantity, cartId: '36c98af9-bee7-4e11-bd19-36426261d727' },
          {
            onSuccess() {
              toast.success(t('cart.add_item_to_cart_successfully'));
            },
            onError(error) {
              toast.error(t('cart.add_item_to_cart_failed') + error);
            },
          }
        );
      } catch (error) {
        toast.error(t('cart.add_item_to_cart_failed') + error);
      }
    } else {
      openModalAuth();
    }
  };

  const handleAddToWishlist = (productId: string) => {
    if (user) {
      try {
        addToWishlist(productId);
        addProductToWishlist(
          { customerId: user.user.accountId, productId: productId },
          {
            onSuccess() {
              toast.success(t('wishlist.add_item_to_wishlist_successfully'));
            },
            onError() {
              toast.error(t('wishlist.add_item_to_wishlist_failed'));
            },
          }
        );
      } catch (error) {
        toast.error(t('wishlist.add_item_to_wishlist_failed') + error);
      }
    } else {
      openModalAuth();
    }
  };

  const handleRemoveFromWishlist = (productId: string) => {
    if (user) {
      try {
        removeFromWishlist(productId);
        removeProductFromWishlist(
          { customerId: user.user.accountId, productId: productId },
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
        toast.error(t('wishlist.remove_item_from_wishlist_failed') + error);
      }
    } else {
      openModalAuth();
    }
  };

  const handleZoom = (event: React.MouseEvent<HTMLDivElement, MouseEvent>) => {
    const rect = event.currentTarget.getBoundingClientRect();

    const image = imageRef.current as HTMLImageElement;
    const { naturalHeight, naturalWidth } = image;
    const { offsetX, offsetY } = event.nativeEvent;
    const top = offsetY * (1 - naturalHeight / rect.height);
    const left = offsetX * (1 - naturalWidth / rect.width);
    image.style.width = naturalWidth + 'px';
    image.style.height = naturalHeight + 'px';
    image.style.maxWidth = 'unset';
    image.style.top = top + 'px';
    image.style.left = left + 'px';
  };

  const handleRemoveZoom = () => {
    imageRef.current?.removeAttribute('style');
  };

  const next = () => {
    if (currentIndexImages[1] < 10) {
      setCurrentIndexImages([currentIndexImages[0] + 1, currentIndexImages[1] + 1]);
    }
  };

  const prev = () => {
    if (currentIndexImages[0] > 0) {
      setCurrentIndexImages([currentIndexImages[0] - 1, currentIndexImages[1] - 1]);
    }
  };

  const data = [
    {
      name: '5 stars',
      uv: Math.round((reviewStatistic.reviewRating5Star / reviewStatistic.totalReview) * 100),
    },
    {
      name: '4 stars',
      uv: Math.round((reviewStatistic.reviewRating4Star / reviewStatistic.totalReview) * 100),
    },
    {
      name: '3 stars',
      uv: Math.round((reviewStatistic.reviewRating3Star / reviewStatistic.totalReview) * 100),
    },
    {
      name: '2 stars',
      uv: Math.round((reviewStatistic.reviewRating2Star / reviewStatistic.totalReview) * 100),
    },
    {
      name: '1 stars',
      uv: Math.round((reviewStatistic.reviewRating1Star / reviewStatistic.totalReview) * 100),
    },
  ];

  const handleSelectOption = (optionId: number, optionValue: string) => {
    if (productOption && productOption.length > 0 && productVariant && productVariant.length > 0) {
      if (currentSelectedOption[+optionId] === optionValue) {
        if (Object.keys(currentSelectedOption).length > 1) {
          setCurrentSelectedOption((prev) => {
            const newOption = { ...prev };
            delete newOption[+optionId];
            return newOption;
          });
          setIsUnchecking(true);
        }
      } else {
        setCurrentSelectedOption({ ...currentSelectedOption, [optionId]: optionValue });
        setOptionSelected({ [optionId]: optionValue });
        setIsUnchecking(false);
      }
    }
  };

  return (
    <div className="w-[80%] mx-auto my-3">
      <div className="bg-white flex px-3 py-2">
        <div className="w-[40%] py-3">
          <div
            className="relative w-full pt-[100%] shadow-sm cursor-zoom-in overflow-hidden"
            onMouseLeave={handleRemoveZoom}
            onMouseMove={handleZoom}
          >
            <Image
              src={currentImage?.imagePath || DefaultImage}
              alt={product.name}
              width={300}
              height={300}
              ref={imageRef}
              className="absolute pointer-events-none top-0 left-0 h-full w-full bg-white object-cover"
            />
          </div>
          <div className="relative mt-4 grid grid-cols-5 gap-0 overflow-hidden">
            <button
              onClick={prev}
              className="absolute left-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={1.5}
                stroke="currentColor"
                className="w-5 h-5"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M15.75 19.5L8.25 12l7.5-7.5" />
              </svg>
            </button>
            <button
              onClick={next}
              className="absolute right-0 top-1/2 z-10 h-9 w-5 -translate-y-1/2 bg-black/20 text-white"
            >
              <svg
                xmlns="http://www.w3.org/2000/svg"
                fill="none"
                viewBox="0 0 24 24"
                strokeWidth={1.5}
                stroke="currentColor"
                className="w-5 h-5"
              >
                <path strokeLinecap="round" strokeLinejoin="round" d="M8.25 4.5l7.5 7.5-7.5 7.5" />
              </svg>
            </button>
            {product.image?.slice(...currentIndexImages).map((img: ProductImage) => {
              const isActive = img === currentImage;
              return (
                <div key={img.id} className="relative w-[5rem] h-[5rem]" onClick={() => setCurrentImage(img)}>
                  <Image
                    src={img.imagePath}
                    alt={product.name}
                    fill
                    objectFit="fill"
                    className={`cursor-pointer ${isActive && 'border-2 border-red-400'}`}
                  />
                </div>
              );
            })}
          </div>
        </div>
        <div className="w-[60%] ml-5 p-3">
          <h1 className="font-medium text-2xl bg-gray-200 px-3 py-2 mb-3">{product.name}</h1>
          <div className="flex justify-between my-3">
            <React.Fragment>
              <div className="flex items-center">
                <div
                  className="flex items-center"
                  onClick={(event) => {
                    setAnchorEl(event.currentTarget);
                  }}
                >
                  <span className="mr-1">{reviewStatistic?.averageRating}</span>
                  <Rating value={reviewStatistic.averageRating} precision={0.1} size="small" readOnly />
                  <Icon component={KeyboardArrowDown} />
                </div>
                <span className="opacity-80 mx-2">|</span>
                <span>
                  {formatNumber(reviewStatistic.totalReview)} {t('product.rating')}
                </span>
                <span className="opacity-80 mx-2">|</span>
                <span>
                  {t('product.sold')}: {sold}
                </span>
              </div>
              <Popover
                open={Boolean(anchorEl)}
                anchorEl={anchorEl}
                onClose={() => {
                  setAnchorEl(null);
                }}
                anchorOrigin={{
                  vertical: 'bottom',
                  horizontal: 'center',
                }}
                transformOrigin={{
                  vertical: 'top',
                  horizontal: 'center',
                }}
              >
                <div className="px-3 py-2 w-96 h-80">
                  <div className="flex items-center">
                    <Rating value={reviewStatistic.averageRating} precision={0.1} size="small" readOnly />
                    <span className="mr-1">{reviewStatistic?.averageRating} out of 5</span>
                  </div>
                  <div>{reviewStatistic.totalReview} ratings</div>
                  <div>
                    {/* <ResponsiveContainer width="100%" height="100%"> */}
                    <BarChart width={300} height={400} data={data} layout="vertical">
                      <Bar dataKey="uv" barSize={20} fill="#8884d8" label />
                      <XAxis hide />
                      {/* <YAxis type="category" dataKey="name" /> */}
                    </BarChart>
                    {/* </ResponsiveContainer> */}
                  </div>
                </div>
              </Popover>
            </React.Fragment>

            {/* <ProductImageGallery listImages={listImages} /> */}

            <div>
              {checkExistItemInWishlist(product.id) ? (
                <span
                  className="hover:cursor-pointer"
                  title={t('common.add_to_wishlist')}
                  onClick={() => handleAddToWishlist(product.id)}
                >
                  <Icon component={HeartEmpty} sx={{ color: 'red' }} />
                </span>
              ) : (
                <span
                  className="hover:cursor-pointer"
                  title={t('common.remove_from_wishlist')}
                  onClick={() => handleRemoveFromWishlist(product.id)}
                >
                  <Icon component={HeartFull} sx={{ color: 'red' }} />
                </span>
              )}
            </div>
          </div>
          <div className="font-semibold text-3xl text-yellow-400">{formatCurrency(10000000000000, 'vi', 'VND')}</div>
          ============
          {/* product options */}
          {(productOption || []).map((productOption) => {
            const optionValues =
              productOptionValueGet?.filter((post) => post.productOptionId === productOption.id) || [];

            return (
              <div className="mb-3" key={productOption.name}>
                <h5 className="mb-2 fs-6">{productOption.name}:</h5>
                {optionValues.length > 0 ? (
                  <div className="d-flex gap-2">
                    {optionValues.map((post) => (
                      <Button
                        key={post.productOptionValue}
                        className={`${
                          currentSelectedOption[productOption.id] === post.productOptionValue
                            ? 'btn btn-primary bg-amber-200'
                            : 'text-dark btn-outline-primary'
                        }`}
                        onClick={() => handleSelectOption(productOption.id, post.productOptionValue)}
                        aria-label={`Select ${productOption.name} ${post.productOptionValue}`}
                        variant="outline"
                      >
                        {post.productOptionValue}
                      </Button>
                    ))}
                  </div>
                ) : (
                  <div>{productOption.name}</div>
                )}
              </div>
            );
          })}
          ===================================
          <div className="flex mt-10">
            <span className="flex items-center mr-10">{t('product.Quantity')}</span>
            <QuantitySelector
              value={quantity}
              max={123456}
              onDecrease={setQuantity}
              onIncrease={setQuantity}
              onType={setQuantity}
            />
            <span className="flex items-center ml-10">
              {product ? <p>12345 {t('common.available')}</p> : <p>{t('common.sold_out')}</p>}
            </span>
          </div>
          <div className="flex mt-10">
            <Button
              className="bg-yellow-50 border-yellow-300 border-2 text-yellow-400"
              onClick={() => handleAddProductToCart()}
            >
              <AddToCartIcon width={28} height={28} />
              <span className="ml-1">{t('common.add_to_cart')}</span>
            </Button>
            <Button className="bg-yellow-400 text-[#fff] ml-3" disabled={12345 <= 0}>
              <span className="mx-3">{t('product.buy_now')}</span>
            </Button>
          </div>
        </div>
      </div>
      {/* <div className="bg-white mt-10 p-5 flex">
        <div className="w-[35%] flex">
          <Avatar src={supplier.avatar} alt="Store Avatar" style={{ width: '6rem', height: '6rem' }} />
          <div className="w-full ml-3">
            <h3 className="mb-2">{supplier.name}</h3>
            <div className="flex">
              <Button className="bg-yellow-50 text-yellow-300 border-solid border-2 border-yellow-300 mr-3">
                <Icon component={ForumOutlined} />
                <span>{t('common.contact')}</span>
              </Button>
              <Link
                href={PATH.SUPPLIER_PATH_URL.SUPPLIER_DATAIL + supplier.slug}
                className=" border-solid border-2 border-gray-300 flex items-center px-2"
              >
                <Icon component={Storefront} />
                <span>{t('store.view_store')}</span>
              </Link>
            </div>
          </div>
        </div>
        <div className="bg-red-400 w-[65%]">asdfasjl</div>
      </div> */}
      <div className="bg-white mt-10 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm">{t('product.product_detail').toUpperCase()}</h2>
        <div className="grid grid-cols-4">
          <div className="col-span-1">{t('common.Category')}</div>
          <div className="col-span-3">{product.category.map((item) => item.name)}</div>
          <div className="col-span-1">{t('common.in_stock')}</div>
          {/* <div className="col-span-3">{product.quantity > 0 ? <p>{product.quantity}</p> : <p>0</p>}</div> */}
        </div>
      </div>
      <div className="bg-white mt-10 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm">{t('product.product_attribute').toUpperCase()}</h2>
        <TableContainer>
          <Table>
            <TableBody>
              {product.attribute?.map((item: ProductAttribute) => (
                <TableRow key={item.id}>
                  <TableCell>{item.attribute}</TableCell>
                  <TableCell>{item.value}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      </div>
      <div className="bg-white mt-10 p-5">
        <h2 className="bg-yellow-100 px-2 py-1 rounded-sm">{t('product.product_description').toUpperCase()}</h2>
        {/* <div className="">{product.description}</div> */}
        <div dangerouslySetInnerHTML={{ __html: product.description }} className="prose" />
      </div>
    </div>
  );
}
