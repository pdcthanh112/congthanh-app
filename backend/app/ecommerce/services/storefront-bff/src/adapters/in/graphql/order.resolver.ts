import { CurrentPrincipal, type Principal } from '@ecom/platform';
import { Args, Context, Int, Mutation, Query, Resolver } from '@nestjs/graphql';
import { StorefrontService } from '../../../application/storefront.service.js';
import { clientIp, type GqlCtx } from './loaders.js';
import { checkoutResult, order, orderSummary } from './mappers.js';
import { CheckoutInput, CheckoutProgressModel, CheckoutResultModel, OrderModel, OrderSummaryModel } from './types.js';

/** Đặt hàng và theo dõi đơn; order-service kiểm quyền xem đơn theo token chuyển tiếp. */
@Resolver(() => OrderModel)
export class OrderResolver {
  constructor(private readonly svc: StorefrontService) {}

  @Mutation(() => CheckoutResultModel, { description: 'Đặt hàng từ giỏ mặc định' })
  async checkout(@CurrentPrincipal() p: Principal, @Context() ctx: GqlCtx, @Args('input') input: CheckoutInput) {
    const ip = clientIp(ctx);
    return checkoutResult(await this.svc.checkout(p, { ...input, ...(ip ? { clientIp: ip } : {}) }));
  }

  @Query(() => CheckoutProgressModel, { description: 'Tiến trình saga đặt hàng (poll mỗi 1-2 giây)' })
  async checkoutProgress(@CurrentPrincipal() p: Principal, @Args('orderCode') orderCode: string) {
    const r = await this.svc.orders.progress(p.token, orderCode);
    return {
      ...r,
      step: r.step ?? null,
      paymentRedirectUrl: r.paymentRedirectUrl ?? null,
      failureReason: r.failureReason ?? null,
    };
  }

  @Query(() => [OrderSummaryModel])
  async myOrders(
    @CurrentPrincipal() p: Principal,
    @Args('limit', { type: () => Int, defaultValue: 20 }) limit: number,
  ) {
    return (await this.svc.orders.history(p.token, Math.min(Math.max(limit, 1), 100))).map(orderSummary);
  }

  @Query(() => OrderModel)
  async order(@CurrentPrincipal() p: Principal, @Args('orderCode') orderCode: string) {
    return order(await this.svc.orders.get(p.token, orderCode));
  }

  @Mutation(() => OrderModel, { description: 'Huỷ đơn khi chưa giao cho vận chuyển' })
  async cancelOrder(
    @CurrentPrincipal() p: Principal,
    @Args('orderCode') orderCode: string,
    @Args('reason', { nullable: true }) reason?: string,
  ) {
    await this.svc.orders.cancel(p.token, orderCode, reason?.slice(0, 500));
    return order(await this.svc.orders.get(p.token, orderCode));
  }
}
