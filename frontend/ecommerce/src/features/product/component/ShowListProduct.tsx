import { Product } from '@/models/types';
import ProductItemCard from './ProductItemCard';
import ProductItemCardSkeleton from './ProductItemCard/ProductItemCardSkeleton';

type ShowListProductProps = {
  listProduct: Product[];
  loading: boolean;
};

export default function ShowListProduct({ listProduct, loading = true }: ShowListProductProps) {
  if (loading)
    return (
      <div className="w-[90%] mx-auto grid gap-4 grid-flow-row-dense grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
        {Array.from(Array(10)).map((_, id) => (
          <ProductItemCardSkeleton key={id} />
        ))}
      </div>
    );

  if (!listProduct) {
    return <div className='flex justify-center'>List empty</div>;
  }

  return (
    <div className="grid gap-4 grid-flow-row-dense grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
      {listProduct.map((product: Product) => (
        <ProductItemCard key={product.id} product={product} />
      ))}
    </div>
  );
}
