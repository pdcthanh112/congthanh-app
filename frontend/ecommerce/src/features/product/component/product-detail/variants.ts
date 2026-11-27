import type { ProductVariant } from '@/graphql/types';

export type OptionSelection = Record<string, string>;

export const selectionOf = (variant: ProductVariant | undefined): OptionSelection =>
  Object.fromEntries((variant?.options ?? []).map((o) => [o.optionId, o.value]));

export const findVariant = (
  variants: readonly ProductVariant[],
  optionCount: number,
  selection: OptionSelection
): ProductVariant | undefined => {
  if (Object.keys(selection).length < optionCount) return undefined;
  return variants.find((v) => v.options.every((o) => selection[o.optionId] === o.value));
};

export const isValueAvailable = (
  variants: readonly ProductVariant[],
  selection: OptionSelection,
  optionId: string,
  value: string
): boolean =>
  variants.some((v) => {
    const inStock = v.stock === null || v.stock.available > 0;
    return (
      inStock &&
      v.options.every((o) =>
        o.optionId === optionId ? o.value === value : !selection[o.optionId] || selection[o.optionId] === o.value
      )
    );
  });
