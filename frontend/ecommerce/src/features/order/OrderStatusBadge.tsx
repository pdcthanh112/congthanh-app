import { useTranslations } from 'next-intl';
import { cn } from '@/lib/utils';

const COLORS: Record<string, string> = {
  PLACED: 'bg-blue-100 text-blue-700',
  PENDING: 'bg-blue-100 text-blue-700',
  PREPARING: 'bg-yellow-100 text-yellow-800',
  SHIPPING: 'bg-orange-100 text-orange-700',
  DELIVERED: 'bg-green-100 text-green-700',
  COMPLETED: 'bg-green-100 text-green-700',
  CANCELLED: 'bg-red-100 text-red-700',
  RETURNED: 'bg-purple-100 text-purple-700',
  FAILED: 'bg-gray-200 text-gray-700',
};

export default function OrderStatusBadge({ status }: { status: string }) {
  const t = useTranslations('order.status');
  return (
    <span className={cn('rounded px-2 py-0.5 text-xs font-medium', COLORS[status] ?? 'bg-gray-100 text-gray-700')}>
      {t.has(status) ? t(status) : status}
    </span>
  );
}
