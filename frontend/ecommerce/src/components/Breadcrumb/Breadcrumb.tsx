import Link from 'next/link';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { Breadcrumb as BreadcrumbType } from '@/models/types';

type PropsType = {
  items: BreadcrumbType[];
};

export default function BreadcrumbComponent({ items }: PropsType) {
  return (
    <Breadcrumb>
      <BreadcrumbList>
        {items.map((item, index) => (
          <BreadcrumbItem key={index}>
            {item.url ? (
              <BreadcrumbLink>
                <Link href={item.url}>{item.pageName}</Link>
              </BreadcrumbLink>
            ) : (
              <BreadcrumbPage>{item.pageName}</BreadcrumbPage>
            )}
            {index < items.length - 1 && <BreadcrumbSeparator />}
          </BreadcrumbItem>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
