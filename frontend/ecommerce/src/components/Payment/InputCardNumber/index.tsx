import React, { useState } from 'react';
import { CardType } from '@/utils/constants/enum';
import Image from 'next/image';
import { FormControl, FormLabel, FormMessage } from '@/components/ui';
import { TextField } from '@mui/material';

const CARD_PATTERNS = [
  { type: 'visa', pattern: /^4/, format: [4, 4, 4, 4], length: 19 },
  { type: 'mastercard', pattern: /^((5[12345])|(2[2-7]))/, format: [4, 4, 4, 4], length: 16 },
  { type: 'amex', pattern: /^3[47]/, format: [4, 6, 5], length: 15 },
  { type: 'jcb', pattern: /^35[2-8]/, format: [4, 4, 4, 4], length: 19 },
  { type: 'discover', pattern: /^(6011|65|64[4-9]|622)/, format: [4, 4, 4, 4], length: 19 },
  { type: 'unknown', pattern: / /, format: [4, 4, 4, 4], length: 19 },
];

type CardInputProps = {
  value: string;
  onChange: (value: string) => void;
  onCardTypeChange?: (type: CardType) => void;
  label?: string;
  error?: string;
  placeholder?: string;
  required?: boolean;
};

export default function InputCardNumber({
  value,
  onChange,
  onCardTypeChange,
  label = 'Card Number',
  error,
  placeholder = 'XXXX XXXX XXXX XXXX',
  required = false,
}: CardInputProps) {
  const [cardType, setCardType] = useState<CardType>('unknown');
  const [focused, setFocused] = useState(false);

  const detectCardType = (cardNumber: string): CardType => {
    const cleanNumber = cardNumber.replace(/\D/g, '');

    if (!cleanNumber) return 'unknown';

    for (const [type, card_pattern] of Object.entries(CARD_PATTERNS)) {
      if (card_pattern.pattern.test(cleanNumber)) {
        return type as CardType;
      }
    }
    return 'unknown';
  };

  // Format số thẻ theo định dạng tương ứng
  const formatCardNumber = (input: string, type: CardType): string => {
    const cleanNumber = input.replace(/\D/g, '');
    const formatPattern = CARD_PATTERNS[type].format;
    let result = '';
    let currentPosition = 0;

    formatPattern.forEach((length: number, index: number) => {
      if (currentPosition >= cleanNumber.length) return;

      const segment = cleanNumber.substring(currentPosition, currentPosition + length);
      if (segment) {
        result += (index !== 0 ? ' ' : '') + segment;
        currentPosition += length;
      }
    });

    return result;
  };

  // Kiểm tra xem số thẻ có hợp lệ cho loại thẻ đã phát hiện
  const isValidForCardType = (input: string, type: CardType): boolean => {
    const cleanNumber = input.replace(/\D/g, '');
    const validLengths = CARD_PATTERNS[type].length;
    return validLengths.includes(cleanNumber.length);
  };

  // Luhn algorithm để kiểm tra tính hợp lệ của số thẻ
  const isValidLuhn = (input: string): boolean => {
    const cleanNumber = input.replace(/\D/g, '');
    if (!cleanNumber) return false;

    let sum = 0;
    let shouldDouble = false;

    // Loop từ phải sang trái
    for (let i = cleanNumber.length - 1; i >= 0; i--) {
      let digit = parseInt(cleanNumber.charAt(i));

      if (shouldDouble) {
        digit *= 2;
        if (digit > 9) digit -= 9;
      }

      sum += digit;
      shouldDouble = !shouldDouble;
    }

    return sum % 10 === 0;
  };

  // Xử lý khi người dùng nhập
  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    let inputValue = e.target.value;

    // Chỉ cho phép nhập số và khoảng trắng
    if (!/^[\d\s]*$/.test(inputValue)) {
      return;
    }

    // Xác định loại thẻ
    const newType = detectCardType(inputValue);

    // Format số thẻ
    const cleanNumber = inputValue.replace(/\D/g, '');
    const maxLength = Math.max(...CARD_PATTERNS[type].length);
    const truncatedNumber = cleanNumber.substring(0, maxLength);
    const formattedValue = formatCardNumber(truncatedNumber, newType);

    // Cập nhật state
    if (cardType !== newType) {
      setCardType(newType);
      onCardTypeChange?.(newType);
    }

    onChange(formattedValue);
  };

  // Validation khi người dùng rời khỏi input
  const validateOnBlur = () => {
    setFocused(false);

    const cleanNumber = value.replace(/\D/g, '');
    if (!cleanNumber) return;

    const isValid = isValidForCardType(cleanNumber, cardType) && isValidLuhn(cleanNumber);

    // Bạn có thể thêm xử lý validation error ở đây
    console.log(`Card validation: ${isValid ? 'Valid' : 'Invalid'} ${cardType} card`);
  };

  return (
    <div className="space-y-2">
      <FormLabel htmlFor="card-number">
        {label} {required && <span className="text-red-500">*</span>}
      </FormLabel>

      <div className="relative">
        <FormControl>
          <TextField
            id="card-number"
            type="text"
            value={value}
            onChange={handleChange}
            onFocus={() => setFocused(true)}
            onBlur={validateOnBlur}
            placeholder={placeholder}
            className={`pl-10 ${error ? 'border-red-500' : ''}`}
            autoComplete="cc-number"
            maxLength={cardType === 'amex' ? 17 : 19} // 15/16 digits + spaces
            required={required}
          />
        </FormControl>

        <div className="absolute left-3 top-1/2 transform -translate-y-1/2">
          {cardType !== 'unknown' && (
            <div className="w-6 h-4">
              {/* Thay thế bằng đúng path tới thư mục chứa icon thẻ của bạn */}
              <Image src={`/icons/cards/${cardType}.svg`} alt={cardType} width={24} height={16} />
            </div>
          )}
        </div>
      </div>

      {error && <FormMessage>{error}</FormMessage>}
    </div>
  );
}
