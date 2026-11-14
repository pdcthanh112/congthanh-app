import Image from 'next/image';
import { useLocale, useTranslations } from 'next-intl';
import StarRating from '@/components/common/StarRating';
import { Avatar, AvatarFallback, Separator } from '@/components/ui';
import { Review } from '@/models/types/Review';
import { formatDateTime } from '@/utils/date';

export default function ReviewComponent({ review }: { review: Review }) {
  const t = useTranslations();
  const locale = useLocale();

  return (
    <article className="mb-5 border-b pb-4">
      <div className="flex items-center gap-2">
        <Avatar>
          <AvatarFallback>{t('product.customer').charAt(0)}</AvatarFallback>
        </Avatar>
        <span>{t('product.customer')}</span>
      </div>
      <div className="my-1 flex flex-wrap items-center text-sm text-muted-foreground">
        <StarRating value={review.rating} size={14} />
        <Separator orientation="vertical" className="mx-2 h-4" />
        <time dateTime={String(review.createdAt)}>{formatDateTime(review.createdAt, locale)}</time>
      </div>
      <p className="whitespace-pre-line">{review.content}</p>
      {review.reviewMedia?.length > 0 && (
        <div className="mt-2 flex flex-wrap gap-3">
          {review.reviewMedia.map((item) => (
            <span key={item.id} className="relative h-20 w-20 overflow-hidden rounded">
              <Image src={item.url} alt="" fill sizes="80px" className="object-cover" />
            </span>
          ))}
        </div>
      )}
    </article>
  );
}
