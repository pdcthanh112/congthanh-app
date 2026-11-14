'use client';

import { useEffect, useState } from 'react';
import { MinusIcon, PlusIcon } from '@/assets/icons';

type PropsType = {
  initialValue: number;
  min?: number;
  max?: number;
  onChange: (value: number) => void;
};

const QuantitySelector = ({ initialValue, min = 1, max = 99, onChange }: PropsType) => {
  const clamp = (v: number) => Math.min(max, Math.max(min, v));
  const [quantity, setQuantity] = useState<number>(clamp(Number(initialValue) || min));
  // Chuỗi đang gõ: cho phép xoá trắng/gõ dở, chỉ chốt giá trị hợp lệ; giá trị dưới min hoặc rỗng được sửa khi blur.
  const [draft, setDraft] = useState<string>(String(quantity));

  useEffect(() => {
    setQuantity(initialValue);
    setDraft(String(initialValue));
  }, [initialValue]);

  const commit = (value: number) => {
    setQuantity(value);
    setDraft(String(value));
    if (value !== quantity) onChange(value);
  };

  const handleDecrease = () => quantity > min && commit(quantity - 1);
  const handleIncrease = () => quantity < max && commit(quantity + 1);

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const raw = e.target.value;
    const value = Number(raw);
    if (raw === '' || !Number.isInteger(value)) return setDraft(raw);
    if (value > max) return commit(max);
    if (value < min) return setDraft(raw);
    commit(value);
  };

  const handleInputBlur = () => {
    const value = Number(draft);
    commit(draft === '' || !Number.isFinite(value) ? min : clamp(Math.trunc(value)));
  };

  return (
    <div className={'flex items-center'}>
      <button
        type="button"
        className="p-2 bg-[#f3f3f3] flex h-8 w-8 items-center justify-center rounded-l-sm border border-gray-300 text-gray-600 cursor-pointer"
        onClick={handleDecrease}
        disabled={quantity <= min}
        aria-label="Decrease quantity"
        aria-controls="quantity-input"
      >
        <MinusIcon />
      </button>
      <input
        type="number"
        id="quantity-input"
        inputMode="numeric"
        value={draft}
        onChange={handleInputChange}
        onBlur={handleInputBlur}
        min={min}
        max={max}
        className="w-16 h-10 text-center border-0 focus:outline-none text-gray-800 font-medium"
        aria-label="Số lượng"
      />
      <button
        type="button"
        className="p-2 bg-[#f3f3f3] flex h-8 w-8 items-center justify-center rounded-r-sm border border-gray-300 text-gray-600 cursor-pointer"
        onClick={handleIncrease}
        disabled={quantity >= max}
        aria-label="Increase quantity"
        aria-controls="quantity-input"
      >
        <PlusIcon />
      </button>
    </div>
  );
};

export default QuantitySelector;
