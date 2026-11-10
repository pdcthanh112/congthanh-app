import type { ProductDetail, ProductSummary, StockLevel } from '@ecom/clients';
import type { Principal } from '@ecom/platform';
import DataLoader from 'dataloader';
import type { StorefrontService } from '../../../application/storefront.service.js';

/**
 * DataLoader theo request: gom mọi Product xuất hiện trong một truy vấn (danh sách, giỏ, đơn,
 * _entities từ router) thành một lần gọi gRPC GetProductsByIds và một lần GetInventoryLevel.
 */
export class Loaders {
  readonly summary: DataLoader<string, ProductSummary | null>;
  readonly detail: DataLoader<string, ProductDetail>;
  readonly stock: DataLoader<string, StockLevel | null>;

  constructor(svc: StorefrontService, principal: Principal | undefined) {
    this.summary = new DataLoader(async (ids) => {
      // includeInactive: sản phẩm đã ngừng bán vẫn hiện trong đơn cũ/giỏ (kèm active=false).
      const map = await svc.products.byIds(ids, true);
      return ids.map((id) => map.get(id) ?? null);
    });
    // product-service chưa có REST theo lô cho chi tiết: gọi song song, tối đa 20 mỗi lô.
    this.detail = new DataLoader(
      (ids) => Promise.all(ids.map((id) => svc.products.detail(id).catch((e: unknown) => e as Error))),
      { maxBatchSize: 20 },
    );
    this.stock = new DataLoader(async (ids) => {
      const map = await svc.inventory.levels(ids, principal?.token);
      return ids.map((id) => map.get(id) ?? null);
    });
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
