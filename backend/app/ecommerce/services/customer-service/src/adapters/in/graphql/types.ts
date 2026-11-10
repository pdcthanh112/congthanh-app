import { Directive, Field, Float, ID, InputType, Int, ObjectType, registerEnumType } from '@nestjs/graphql';

export enum AddressLabelGql {
  HOME = 'HOME',
  OFFICE = 'OFFICE',
  OTHER = 'OTHER',
}
registerEnumType(AddressLabelGql, { name: 'AddressLabel' });

@ObjectType('ShippingAddress')
export class ShippingAddressModel {
  @Field(() => ID) id!: string;
  @Field() fullName!: string;
  @Field() phoneNumber!: string;
  @Field(() => AddressLabelGql) label!: AddressLabelGql;
  @Field({ description: 'ISO 3166-1 alpha-2' }) countryCode!: string;
  @Field(() => String, { nullable: true }) provinceCode!: string | null;
  @Field(() => String, { nullable: true }) districtCode!: string | null;
  @Field(() => String, { nullable: true }) wardCode!: string | null;
  @Field() addressLine!: string;
  @Field(() => String, { nullable: true }) postalCode!: string | null;
  @Field(() => Float, { nullable: true }) latitude!: number | null;
  @Field(() => Float, { nullable: true }) longitude!: number | null;
  @Field() isDefault!: boolean;
  @Field() updatedAt!: Date;
}

/** Product do storefront-bff sở hữu; customer chỉ tham chiếu (và góp `inWishlist`). */
@ObjectType('Product')
@Directive('@key(fields: "id")')
export class ProductRef {
  @Field(() => ID) id!: string;
}

@ObjectType('WishlistItem')
export class WishlistItemModel {
  @Field(() => ProductRef) product!: ProductRef;
  @Field() addedAt!: Date;
}

@ObjectType('WishlistPage')
export class WishlistPageModel {
  @Field(() => [WishlistItemModel]) items!: WishlistItemModel[];
  @Field(() => Int) total!: number;
}

@ObjectType('SearchEntry')
export class SearchEntryModel {
  @Field() keyword!: string;
  @Field() searchedAt!: Date;
}

/** Hồ sơ khách (id = sub Keycloak). Thông tin tên/email do Keycloak giữ; service khác góp thêm trường. */
@ObjectType('Customer')
@Directive('@key(fields: "id")')
export class CustomerModel {
  @Field(() => ID) id!: string;
}

@InputType()
export class AddressInput {
  @Field() fullName!: string;
  @Field() phoneNumber!: string;
  @Field(() => AddressLabelGql, { defaultValue: AddressLabelGql.HOME }) label!: AddressLabelGql;
  @Field({ defaultValue: 'VN' }) countryCode!: string;
  @Field(() => String, { nullable: true }) provinceCode?: string | null;
  @Field(() => String, { nullable: true }) districtCode?: string | null;
  @Field(() => String, { nullable: true }) wardCode?: string | null;
  @Field() addressLine!: string;
  @Field(() => String, { nullable: true }) postalCode?: string | null;
  @Field(() => Float, { nullable: true }) latitude?: number | null;
  @Field(() => Float, { nullable: true }) longitude?: number | null;
}
