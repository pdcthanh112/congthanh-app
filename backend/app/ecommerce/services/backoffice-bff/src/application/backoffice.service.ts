import type { AdjustmentKind, StockLevel } from '@ecom/clients';
import { DomainError, hasAnyRole, type Principal, Role } from '@ecom/platform';
import { Inject, Injectable } from '@nestjs/common';
import {
  CARTS,
  type CartPort,
  INVENTORY,
  type InventoryPort,
  ORDERS,
  type OrderPort,
  PRODUCTS,
  PROMOTIONS,
  type ProductPort,
  type PromotionPort,
} from './ports.js';

export const MAX_ADJUSTMENT = 100_000;

export interface AdjustCommand {
  productId: string;
  change: number;
  kind: AdjustmentKind;
  reason: string;
  warehouseId?: string | null;
}

/**
 * Nghiệp vụ trang quản trị. Quy tắc phân quyền nằm ở đây (không chỉ ở resolver) để mọi lối vào
 * đều áp dụng: SELLER chỉ thao tác trên sản phẩm của mình, ADMIN thao tác mọi sản phẩm.
 */
@Injectable()
export class BackofficeService {
  constructor(
    @Inject(PRODUCTS) readonly products: ProductPort,
    @Inject(INVENTORY) readonly inventory: InventoryPort,
    @Inject(CARTS) private readonly carts: CartPort,
    @Inject(PROMOTIONS) private readonly promotions: PromotionPort,
    @Inject(ORDERS) readonly orders: OrderPort,
  ) {}

  async adjustInventory(p: Principal, cmd: AdjustCommand): Promise<{ newQuantity: number; stock: StockLevel | null }> {
    if (!Number.isInteger(cmd.change) || cmd.change === 0 || Math.abs(cmd.change) > MAX_ADJUSTMENT) {
      throw DomainError.invalid(`change must be a non-zero integer within ±${MAX_ADJUSTMENT}`);
    }
    const reason = cmd.reason.trim();
    if (reason.length < 3) throw DomainError.invalid('reason is required (at least 3 characters)');
    const sign = Math.sign(cmd.change);
    if ((cmd.kind === 'RESTOCK' || cmd.kind === 'FOUND' || cmd.kind === 'RETURN') && sign < 0) {
      throw DomainError.invalid(`${cmd.kind} must increase stock`);
    }
    if ((cmd.kind === 'DAMAGE' || cmd.kind === 'LOSS') && sign > 0) {
      throw DomainError.invalid(`${cmd.kind} must decrease stock`);
    }
    await this.assertCanManage(p, cmd.productId);
    const r = await this.inventory.adjust(
      { productId: cmd.productId, change: cmd.change, kind: cmd.kind, reason, warehouseId: cmd.warehouseId ?? '' },
      p.id,
      p.token,
    );
    if (!r.success) throw DomainError.conflict(r.message || 'adjustment rejected', 'ADJUSTMENT_REJECTED');
    const stock = (await this.inventory.levels([cmd.productId], p.token)).get(cmd.productId) ?? null;
    return { newQuantity: r.newQuantity, stock };
  }

  /** SELLER chỉ được quản lý sản phẩm do mình bán. */
  async assertCanManage(p: Principal, productId: string): Promise<void> {
    const product = (await this.products.byIds([productId], true)).get(productId);
    if (!product) throw DomainError.notFound(`product ${productId} not found`);
    if (hasAnyRole(p, [Role.ADMIN])) return;
    if (hasAnyRole(p, [Role.SELLER]) && product.sellerId === p.id) return;
    throw DomainError.forbidden('you can only manage your own products');
  }

  promotion(p: Principal, code: string) {
    return this.promotions.byCode(code.trim().toUpperCase(), p.token);
  }

  /** Giỏ hiện tại của một khách, cho bộ phận hỗ trợ khi khách báo lỗi đặt hàng. */
  customerCart(p: Principal, customerId: string) {
    return this.carts.defaultCart(customerId, p.token);
  }
}
