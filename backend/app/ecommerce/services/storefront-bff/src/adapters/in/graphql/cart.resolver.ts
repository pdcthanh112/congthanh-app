import { CurrentPrincipal, type Principal } from '@ecom/platform';
import { Args, Context, ID, Int, Mutation, Query, Resolver } from '@nestjs/graphql';
import { StorefrontService } from '../../../application/storefront.service.js';
import { type GqlCtx, loadersOf } from './loaders.js';
import { cart, money } from './mappers.js';
import { CartModel, PromotionQuoteModel } from './types.js';

/** Giỏ hàng của khách đang đăng nhập (mọi thao tác cần token; cart-service kiểm chủ sở hữu). */
@Resolver(() => CartModel)
export class CartResolver {
  constructor(private readonly svc: StorefrontService) {}

  @Query(() => CartModel)
  async myCart(@CurrentPrincipal() p: Principal, @Context() ctx: GqlCtx) {
    return this.view(await this.svc.cart(p), ctx);
  }

  @Query(() => PromotionQuoteModel, { description: 'Thử mã giảm giá trên giỏ hiện tại' })
  async quotePromotion(@CurrentPrincipal() p: Principal, @Args('code') code: string): Promise<PromotionQuoteModel> {
    const q = await this.svc.quotePromotion(p, code);
    return {
      valid: q.valid,
      reason: q.reason,
      subtotal: money(q.subtotal),
      discount: q.discount ? money(q.discount) : null,
      total: money(q.total),
    };
  }

  @Mutation(() => CartModel)
  async addToCart(
    @CurrentPrincipal() p: Principal,
    @Context() ctx: GqlCtx,
    @Args('productId', { type: () => ID }) productId: string,
    @Args('quantity', { type: () => Int, defaultValue: 1 }) quantity: number,
  ) {
    return this.view(await this.svc.addToCart(p, productId, quantity), ctx);
  }

  @Mutation(() => CartModel, { description: 'quantity = 0 thì xoá dòng' })
  async updateCartItem(
    @CurrentPrincipal() p: Principal,
    @Context() ctx: GqlCtx,
    @Args('itemId', { type: () => ID }) itemId: string,
    @Args('quantity', { type: () => Int }) quantity: number,
  ) {
    return this.view(await this.svc.updateItem(p, Number(itemId), quantity), ctx);
  }

  @Mutation(() => CartModel)
  async removeCartItem(
    @CurrentPrincipal() p: Principal,
    @Context() ctx: GqlCtx,
    @Args('itemId', { type: () => ID }) itemId: string,
  ) {
    return this.view(await this.svc.removeItem(p, Number(itemId)), ctx);
  }

  @Mutation(() => CartModel)
  async clearCart(@CurrentPrincipal() p: Principal, @Context() ctx: GqlCtx) {
    return this.view(await this.svc.clearCart(p), ctx);
  }

  /** Giỏ đã nạp sẵn thông tin sản phẩm: mồi DataLoader để trường Product không gọi lại gRPC. */
  private view(snapshot: Awaited<ReturnType<StorefrontService['cart']>>, ctx: GqlCtx): CartModel {
    const loaders = loadersOf(ctx, this.svc);
    for (const l of snapshot.lines) if (l.product) loaders.summary.prime(l.productId, l.product);
    return cart(snapshot);
  }
}
