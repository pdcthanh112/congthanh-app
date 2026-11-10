import type { ProductDetail, ProductSummary, StockLevel } from '@ecom/clients';
import type { Principal } from '@ecom/platform';
import DataLoader from 'dataloader';
import type { BackofficeService } from '../../../application/backoffice.service.js';

/** DataLoader theo request: gom Product/tồn kho trong một truy vấn thành một lần gọi mỗi service. */
export class Loaders {
  readonly summary: DataLoader<string, ProductSummary | null>;
  readonly detail: DataLoader<string, ProductDetail>;
  readonly stock: DataLoader<string, StockLevel | null>;

  constructor(svc: BackofficeService, principal: Principal | undefined) {
    this.summary = new DataLoader(async (ids) => {
      const map = await svc.products.byIds(ids, true);
      return ids.map((id) => map.get(id) ?? null);
    });
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
  req?: { principal?: Principal };
  loaders?: Loaders;
}

export function loadersOf(ctx: GqlCtx, svc: BackofficeService): Loaders {
  ctx.loaders ??= new Loaders(svc, ctx.req?.principal);
  return ctx.loaders;
}
