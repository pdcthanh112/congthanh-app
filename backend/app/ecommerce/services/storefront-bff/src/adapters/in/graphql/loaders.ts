import type { ProductDetail, ProductOptionValue, ProductSummary, ProductVariant, StockLevel } from '@ecommerce/clients';
import type { Principal } from '@ecommerce/platform';
import DataLoader from 'dataloader';
import type { StorefrontService } from '../../../application/storefront.service.js';

export class Loaders {
  readonly summary: DataLoader<string, ProductSummary | null>;
  readonly detail: DataLoader<string, ProductDetail>;
  readonly stock: DataLoader<string, StockLevel | null>;
  /** Biến thể và giá trị option chỉ cần cho trang chi tiết (một sản phẩm), nên không gom lô mà chỉ nhớ trong request. */
  readonly variants: DataLoader<string, ProductVariant[]>;
  readonly optionValues: DataLoader<string, ProductOptionValue[]>;

  constructor(svc: StorefrontService, principal: Principal | undefined) {
    this.summary = new DataLoader(async (ids) => {
      const map = await svc.products.byIds(ids, true);
      return ids.map((id) => map.get(id) ?? null);
    });

    this.detail = new DataLoader((ids) => Promise.all(ids.map((id) => svc.products.detail(id).catch((e: unknown) => e as Error))), {
      maxBatchSize: 20,
    });
    this.stock = new DataLoader(async (ids) => {
      const map = await svc.inventory.levels(ids, principal?.token);
      return ids.map((id) => map.get(id) ?? null);
    });
    this.variants = new DataLoader((ids) => Promise.all(ids.map((id) => svc.products.variants(id).catch((e: unknown) => e as Error))));
    this.optionValues = new DataLoader((ids) => Promise.all(ids.map((id) => svc.products.optionValues(id).catch((e: unknown) => e as Error))));
  }
}

export interface GqlCtx {
  req?: { headers: Record<string, string | string[] | undefined>; principal?: Principal; ip?: string };
  loaders?: Loaders;
}

export function loadersOf(ctx: GqlCtx, svc: StorefrontService): Loaders {
  ctx.loaders ??= new Loaders(svc, ctx.req?.principal);
  return ctx.loaders;
}

export function clientIp(ctx: GqlCtx): string | undefined {
  const fwd = ctx.req?.headers['x-forwarded-for'];
  const first = (Array.isArray(fwd) ? fwd[0] : fwd)?.split(',')[0]?.trim();
  return first || ctx.req?.ip;
}
