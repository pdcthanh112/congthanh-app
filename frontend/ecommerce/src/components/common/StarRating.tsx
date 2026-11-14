import React from 'react';
import { Star } from 'lucide-react';
import { cn } from '@/lib/utils';

type Props = {
  value: number;
  max?: number;
  size?: number;
  className?: string;
  /** Nhãn đọc cho trình đọc màn hình, ví dụ "4.5 / 5". Mặc định tạo từ value. */
  label?: string;
};

/** Hiển thị điểm đánh giá (chỉ đọc), hỗ trợ phần lẻ. Thay cho Rating của MUI / Rate của antd. */
export default function StarRating({ value, max = 5, size = 16, className, label }: Props) {
  const safe = Math.max(0, Math.min(max, Number.isFinite(value) ? value : 0));
  return (
    <span role="img" aria-label={label ?? `${safe.toFixed(1)} / ${max}`} className={cn('inline-flex', className)}>
      {Array.from({ length: max }, (_, i) => {
        const fill = Math.max(0, Math.min(1, safe - i));
        return (
          <span key={i} className="relative inline-block" style={{ width: size, height: size }} aria-hidden>
            <Star size={size} className="absolute inset-0 text-gray-300" />
            <span className="absolute inset-0 overflow-hidden" style={{ width: `${fill * 100}%` }}>
              <Star size={size} className="text-yellow-400 fill-yellow-400" />
            </span>
          </span>
        );
      })}
    </span>
  );
}
