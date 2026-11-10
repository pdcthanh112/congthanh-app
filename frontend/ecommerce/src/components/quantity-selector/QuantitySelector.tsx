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
  const [quantity, setQuantity] = useState<number>(Number(initialValue || 1));

  useEffect(() => {
    setQuantity(initialValue);
  }, [initialValue]);

  const handleDecrease = () => {
    if (quantity > min) {
      const newQuantity = quantity - 1;
      setQuantity(newQuantity);
      onChange(newQuantity);
    }
  };

  const handleIncrease = () => {
    let newQuantity = quantity + 1;
    if (max && quantity >= max) {
      newQuantity = max;
    }

    setQuantity(newQuantity);
    onChange(newQuantity);
  };

  const handleInputChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = parseInt(e.target.value);
    if (value >= min && value <= max) {
      setQuantity(value);
      onChange(value);
    } else if (value > max) {
      setQuantity(max);
      onChange(max);
    } else {
      setQuantity(min);
      onChange(min);
    }
  };

  const handleInputBlur = () => {
    if (quantity < min) {
      setQuantity(min);
      onChange(min);
    } else if (quantity > max) {
      setQuantity(max);
      onChange(max);
    }
  };

  return (
    <div className={'flex items-center'}>
      <button
        className="p-2 bg-[#f3f3f3] flex h-8 w-8 items-center justify-center rounded-l-sm border border-gray-300 text-gray-600 cursor-pointer"
        onClick={handleDecrease}
        disabled={initialValue <= min}
        aria-label="Decrease quantity"
        aria-controls="quantity-input"
      >
        <MinusIcon />
      </button>
      <input
        type="number"
        value={quantity}
        onChange={handleInputChange}
        onBlur={handleInputBlur}
        min={min}
        max={max}
        className="w-16 h-10 text-center border-0 focus:outline-none text-gray-800 font-medium"
        aria-label="Số lượng"
      />
      <button
        className="p-2 bg-[#f3f3f3] flex h-8 w-8 items-center justify-center rounded-r-sm border border-gray-300 text-gray-600 cursor-pointer"
        onClick={handleIncrease}
        disabled={initialValue >= max!}
        aria-label="Increase quantity"
        aria-controls="quantity-input"
      >
        <PlusIcon />
      </button>
    </div>
  );
};

export default QuantitySelector;
