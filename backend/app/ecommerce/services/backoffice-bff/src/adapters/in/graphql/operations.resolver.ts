import { CurrentPrincipal, type Principal, Role, Roles } from '@ecom/platform';
import { Args, ID, Mutation, Query, Resolver } from '@nestjs/graphql';
import { BackofficeService } from '../../../application/backoffice.service.js';
import { order, stockLevel } from './mappers.js';
import {
  AdjustInventoryInput,
  CustomerCartModel,
  InventoryAdjustmentModel,
  OrderModel,
  PromotionModel,
} from './types.js';

/** Thao tác vận hành: kho, khuyến mãi, hỗ trợ khách (giỏ, đơn). */
@Resolver()
export class OperationsResolver {
  constructor(private readonly svc: BackofficeService) {}

  @Mutation(() => InventoryAdjustmentModel, { description: 'SELLER chỉ điều chỉnh sản phẩm của mình' })
  @Roles(Role.ADMIN, Role.SELLER)
  async adjustInventory(@CurrentPrincipal() p: Principal, @Args('input') input: AdjustInventoryInput) {
    const r = await this.svc.adjustInventory(p, input);
    return { newQuantity: r.newQuantity, stock: r.stock ? stockLevel(r.stock) : null };
  }

  @Query(() => PromotionModel)
  @Roles(Role.ADMIN)
  promotion(@CurrentPrincipal() p: Principal, @Args('code') code: string) {
    return this.svc.promotion(p, code);
  }

  @Query(() => CustomerCartModel, { nullable: true })
  @Roles(Role.ADMIN, Role.SUPPORT)
  async customerCart(
    @CurrentPrincipal() p: Principal,
    @Args('customerId', { type: () => ID }) customerId: string,
  ): Promise<CustomerCartModel | null> {
    const c = await this.svc.customerCart(p, customerId);
    if (!c) return null;
    return {
      id: c.id,
      customerId: c.customerId,
      lines: c.items.map((i) => ({ id: i.id, product: { id: i.productId }, quantity: i.quantity, addedAt: i.addedAt })),
    };
  }

  @Query(() => OrderModel, { description: 'Xem đơn bất kỳ (order-service cho phép ADMIN/SUPPORT)' })
  @Roles(Role.ADMIN, Role.SUPPORT)
  async adminOrder(@CurrentPrincipal() p: Principal, @Args('orderCode') orderCode: string) {
    return order(await this.svc.orders.get(p.token, orderCode));
  }

  @Mutation(() => OrderModel)
  @Roles(Role.ADMIN, Role.SUPPORT)
  async adminCancelOrder(
    @CurrentPrincipal() p: Principal,
    @Args('orderCode') orderCode: string,
    @Args('reason') reason: string,
  ) {
    await this.svc.orders.cancel(p.token, orderCode, reason.slice(0, 500));
    return order(await this.svc.orders.get(p.token, orderCode));
  }
}
