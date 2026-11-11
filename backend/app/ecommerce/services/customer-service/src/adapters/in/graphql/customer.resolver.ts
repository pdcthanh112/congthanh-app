import { CurrentPrincipal, DomainError, type Principal, Public } from '@ecommerce/platform';
import {
  Args,
  Context,
  ID,
  Int,
  Mutation,
  Parent,
  Query,
  ResolveField,
  ResolveReference,
  Resolver,
} from '@nestjs/graphql';
import DataLoader from 'dataloader';
import { CustomerService } from '../../../application/customer.service.js';
import type { AddressDraft, ShippingAddressProps } from '../../../domain/shipping-address.js';
import {
  AddressInput,
  type AddressLabelGql,
  CustomerModel,
  ProductRef,
  SearchEntryModel,
  ShippingAddressModel,
  WishlistPageModel,
} from './types.js';

const toModel = (a: ShippingAddressProps): ShippingAddressModel => ({ ...a, label: a.label as AddressLabelGql });
const toDraft = (i: AddressInput): AddressDraft => ({
  fullName: i.fullName,
  phoneNumber: i.phoneNumber,
  label: i.label,
  countryCode: i.countryCode,
  provinceCode: i.provinceCode ?? null,
  districtCode: i.districtCode ?? null,
  wardCode: i.wardCode ?? null,
  addressLine: i.addressLine,
  postalCode: i.postalCode ?? null,
  latitude: i.latitude ?? null,
  longitude: i.longitude ?? null,
});

/** Chỉ chính chủ đọc được dữ liệu của mình (Customer có thể bị router hỏi qua _entities với id bất kỳ). */
function ownerOnly(principal: Principal | undefined, customerId: string): string {
  if (!principal) throw DomainError.unauthenticated('authentication required');
  if (principal.id !== customerId) throw DomainError.forbidden('cannot read another customer');
  return customerId;
}

type Ctx = { wishlistLoader?: DataLoader<string, boolean> };

@Resolver(() => CustomerModel)
export class CustomerResolver {
  constructor(private readonly customers: CustomerService) {}

  @Query(() => CustomerModel, { description: 'Khách đang đăng nhập' })
  me(@CurrentPrincipal() principal: Principal): CustomerModel {
    return { id: principal.id };
  }

  @ResolveReference()
  resolveReference(ref: { id: string }): CustomerModel {
    return { id: ref.id };
  }

  @ResolveField(() => [ShippingAddressModel])
  async shippingAddresses(@Parent() c: CustomerModel, @CurrentPrincipal() p: Principal) {
    return (await this.customers.read.addresses.listByCustomer(ownerOnly(p, c.id))).map(toModel);
  }

  @ResolveField(() => ShippingAddressModel, { nullable: true })
  async defaultShippingAddress(@Parent() c: CustomerModel, @CurrentPrincipal() p: Principal) {
    const a = await this.customers.read.addresses.findDefault(ownerOnly(p, c.id));
    return a ? toModel(a) : null;
  }

  @ResolveField(() => WishlistPageModel)
  async wishlist(
    @Parent() c: CustomerModel,
    @CurrentPrincipal() p: Principal,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 20 }) size: number,
  ): Promise<WishlistPageModel> {
    const res = await this.customers.wishlist(ownerOnly(p, c.id), page, size);
    return { total: res.total, items: res.items.map((i) => ({ product: { id: i.productId }, addedAt: i.addedAt })) };
  }

  @ResolveField(() => [SearchEntryModel])
  recentSearches(
    @Parent() c: CustomerModel,
    @CurrentPrincipal() p: Principal,
    @Args('limit', { type: () => Int, defaultValue: 10 }) limit: number,
  ) {
    return this.customers.recentSearches(ownerOnly(p, c.id), limit);
  }

  @Mutation(() => ShippingAddressModel)
  async addShippingAddress(
    @CurrentPrincipal() p: Principal,
    @Args('input') input: AddressInput,
    @Args('makeDefault', { defaultValue: false }) makeDefault: boolean,
  ) {
    return toModel(await this.customers.addAddress(p.id, toDraft(input), makeDefault));
  }

  @Mutation(() => ShippingAddressModel)
  async updateShippingAddress(
    @CurrentPrincipal() p: Principal,
    @Args('id', { type: () => ID }) id: string,
    @Args('input') input: AddressInput,
  ) {
    return toModel(await this.customers.updateAddress(p.id, id, toDraft(input)));
  }

  @Mutation(() => ShippingAddressModel)
  async setDefaultShippingAddress(@CurrentPrincipal() p: Principal, @Args('id', { type: () => ID }) id: string) {
    return toModel(await this.customers.setDefaultAddress(p.id, id));
  }

  @Mutation(() => Boolean)
  deleteShippingAddress(@CurrentPrincipal() p: Principal, @Args('id', { type: () => ID }) id: string) {
    return this.customers.deleteAddress(p.id, id);
  }

  @Mutation(() => Boolean)
  addToWishlist(@CurrentPrincipal() p: Principal, @Args('productId', { type: () => ID }) productId: string) {
    return this.customers.addToWishlist(p.id, productId);
  }

  @Mutation(() => Boolean)
  removeFromWishlist(@CurrentPrincipal() p: Principal, @Args('productId', { type: () => ID }) productId: string) {
    return this.customers.removeFromWishlist(p.id, productId);
  }

  @Mutation(() => Boolean, { description: 'Ghi từ khoá vừa tìm (storefront gọi sau khi tìm kiếm)' })
  async recordSearch(@CurrentPrincipal() p: Principal, @Args('keyword') keyword: string) {
    await this.customers.recordSearch(p.id, keyword);
    return true;
  }

  @Mutation(() => Boolean)
  async removeSearch(@CurrentPrincipal() p: Principal, @Args('keyword') keyword: string) {
    await this.customers.removeSearch(p.id, keyword);
    return true;
  }

  @Mutation(() => Boolean)
  async clearSearchHistory(@CurrentPrincipal() p: Principal) {
    await this.customers.clearSearches(p.id);
    return true;
  }
}

/** Góp `Product.inWishlist` cho người đang đăng nhập (false khi khách vãng lai). */
@Resolver(() => ProductRef)
export class ProductWishlistResolver {
  constructor(private readonly customers: CustomerService) {}

  @ResolveReference()
  resolveReference(ref: { id: string }): ProductRef {
    return { id: ref.id };
  }

  @ResolveField(() => Boolean)
  @Public()
  async inWishlist(@Parent() product: ProductRef, @CurrentPrincipal() p: Principal | undefined, @Context() ctx: Ctx) {
    if (!p) return false;
    ctx.wishlistLoader ??= new DataLoader(async (ids: readonly string[]) => {
      const set = await this.customers.read.wishlist.has(p.id, ids);
      return ids.map((id) => set.has(id));
    });
    return ctx.wishlistLoader.load(product.id);
  }
}
