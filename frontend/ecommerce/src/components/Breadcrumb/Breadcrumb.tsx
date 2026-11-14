import React from 'react';
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from '@/components/ui/breadcrumb';
import { Link } from '@/i18n/routing';
import { Breadcrumb as BreadcrumbType } from '@/models/types';

export default function BreadcrumbComponent({ items }: { items: BreadcrumbType[] }) {
  return (
    <Breadcrumb className="w-full max-w-6xl mx-auto px-4 pt-3">
      <BreadcrumbList>
        {items.map((item, index) => (
          <React.Fragment key={index}>
            <BreadcrumbItem>
              {item.url ? (
                <BreadcrumbLink asChild>
                  <Link href={item.url}>{item.pageName}</Link>
                </BreadcrumbLink>
              ) : (
                <BreadcrumbPage>{item.pageName}</BreadcrumbPage>
              )}
            </BreadcrumbItem>
            {index < items.length - 1 && <BreadcrumbSeparator />}
          </React.Fragment>
        ))}
      </BreadcrumbList>
    </Breadcrumb>
  );
}
