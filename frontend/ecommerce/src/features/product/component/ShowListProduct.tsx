import { useTranslations } from 'next-intl';
import { Product } from '@/models/types';
import ProductItemCard from './ProductItemCard';
import ProductItemCardSkeleton from './ProductItemCard/ProductItemCardSkeleton';

type ShowListProductProps = {
  listProduct?: Product[];
  loading?: boolean;
  skeletonCount?: number;
};

const GRID = 'grid gap-4 grid-cols-2 md:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5';

export default function ShowListProduct({ listProduct, loading = false, skeletonCount = 10 }: ShowListProductProps) {
  const t = useTranslations();

  if (loading)
    return (
      <div className={GRID} aria-busy>
        {Array.from({ length: skeletonCount }, (_, id) => (
          <ProductItemCardSkeleton key={id} />
        ))}
      </div>
    );

  if (!listProduct?.length) return <div className="flex justify-center py-10">{t('product.empty')}</div>;

  return (
    <div className={GRID}>
      {listProduct.map((product, index) => (
        <ProductItemCard key={product.id} product={product} priority={index < 4} />
      ))}
    </div>
  );
}
