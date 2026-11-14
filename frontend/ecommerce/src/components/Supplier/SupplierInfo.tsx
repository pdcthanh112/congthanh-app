import Image from 'next/image';
import { useTranslations } from 'next-intl';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui';
import { Supplier } from '@/models/types';

export default function SupplierInfo({ supplier }: { supplier: Supplier }) {
  const t = useTranslations('supplier');

  return (
    <section className="mx-auto my-6 w-full max-w-6xl px-4">
      <div className="relative h-40 w-full overflow-hidden rounded-lg bg-muted sm:h-56">
        {supplier.background && (
          <Image src={supplier.background} alt="" fill priority sizes="100vw" className="object-cover opacity-90" />
        )}
        <div className="absolute inset-0 flex items-end gap-3 bg-gradient-to-t from-black/60 to-transparent p-4 text-white">
          <Avatar className="h-16 w-16 sm:h-20 sm:w-20">
            {supplier.avatar && <AvatarImage src={supplier.avatar} alt={supplier.name} />}
            <AvatarFallback>{supplier.name.charAt(0)}</AvatarFallback>
          </Avatar>
          <h1 className="text-lg font-semibold sm:text-2xl">{supplier.name}</h1>
        </div>
      </div>
      <h2 className="mb-3 mt-6 text-lg font-semibold">{t('products')}</h2>
      <p className="text-muted-foreground">{t('products_coming_soon')}</p>
    </section>
  );
}
