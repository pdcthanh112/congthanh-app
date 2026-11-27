'use client';

import { Button } from '@/components/ui';
import type { ProductOption, ProductVariant } from '@/graphql/types';
import { cn } from '@/lib/utils';
import { useTranslations } from 'next-intl';
import { isValueAvailable, type OptionSelection } from './variants';

interface VariantPickerProps {
  options: ProductOption[];
  variants: ProductVariant[];
  selection: OptionSelection;
  onToggle: (optionId: string, value: string) => void;
}

export default function VariantPicker({ options, variants, selection, onToggle }: VariantPickerProps) {
  const t = useTranslations();

  return (
    <>
      {options.map((option) => (
        <fieldset className="my-3" key={option.id}>
          <legend className="mb-2">{option.name}:</legend>
          <div className="flex flex-wrap gap-2">
            {option.values.map((value) => {
              const selected = selection[option.id] === value;
              return (
                <Button
                  key={value}
                  type="button"
                  variant="outline"
                  aria-pressed={selected}
                  disabled={!selected && !isValueAvailable(variants, selection, option.id, value)}
                  className={cn(selected && 'bg-amber-200')}
                  onClick={() => onToggle(option.id, value)}
                  aria-label={t('product.select_option', { name: option.name, value })}
                >
                  {value}
                </Button>
              );
            })}
          </div>
        </fieldset>
      ))}
    </>
  );
}
