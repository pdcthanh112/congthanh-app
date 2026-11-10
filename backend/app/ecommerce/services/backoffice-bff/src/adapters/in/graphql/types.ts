import {
  Directive,
  Field,
  Float,
  GraphQLISODateTime,
  ID,
  InputType,
  Int,
  ObjectType,
  registerEnumType,
} from '@nestjs/graphql';

@ObjectType('Money')
@Directive('@shareable')
export class MoneyModel {
  @Field(() => Float) amount!: number;
  @Field({ description: 'ISO 4217' }) currency!: string;
  @Field() formatted!: string;
}

export enum StockStatusGql {
  IN_STOCK = 'IN_STOCK',
  LOW_STOCK = 'LOW_STOCK',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  BACKORDER = 'BACKORDER',
  DISCONTINUED = 'DISCONTINUED',
}
registerEnumType(StockStatusGql, { name: 'StockStatus' });

@ObjectType('StockLevel', { description: 'Tồn kho đầy đủ cho người vận hành' })
export class StockLevelModel {
  @Field(() => ID) productId!: string;
  @Field(() => StockStatusGql) status!: StockStatusGql;
  @Field(() => Int) available!: number;
  @Field(() => Int, { description: 'Đang giữ cho đơn chưa thanh toán xong' }) reserved!: number;
  @Field(() => Int) total!: number;
  @Field(() => Int) reorderPoint!: number;
  @Field({ description: 'available <= reorderPoint' }) needsReorder!: boolean;
}

@ObjectType('Category')
@Directive('@key(fields: "id", resolvable: false)')
export class CategoryRef {
  @Field(() => ID) id!: string;
}

export interface ProductSource {
  id: string;
}

@ObjectType('Product', { description: 'Sản phẩm, góc nhìn quản trị' })
@Directive('@key(fields: "id")')
export class ProductModel {
  @Field(() => ID) id!: string;
  @Field() sku!: string;
  @Field() name!: string;
  @Field(() => String, { nullable: true }) slug!: string | null;
  @Field(() => String, { nullable: true, description: 'Trạng thái biên tập ở product-service' }) status!: string | null;
  @Field(() => String, { nullable: true }) thumbnail!: string | null;
  @Field(() => String, { nullable: true }) brandName!: string | null;
  @Field(() => MoneyModel) price!: MoneyModel;
  @Field(() => MoneyModel) originalPrice!: MoneyModel;
  @Field() active!: boolean;
  @Field(() => ID) sellerId!: string;
  @Field(() => [CategoryRef]) categories!: CategoryRef[];
  @Field(() => StockLevelModel, { nullable: true }) stock!: StockLevelModel | null;
}

@ObjectType('ProductPage')
export class ProductPageModel {
  @Field(() => [ProductModel]) items!: ProductSource[];
  @Field(() => Int) page!: number;
  @Field(() => Int) limit!: number;
  @Field(() => Int) totalPages!: number;
  @Field(() => Int) totalItems!: number;
}

export enum AdjustmentKindGql {
  RESTOCK = 'RESTOCK',
  DAMAGE = 'DAMAGE',
  LOSS = 'LOSS',
  FOUND = 'FOUND',
  RETURN = 'RETURN',
  CORRECTION = 'CORRECTION',
}
registerEnumType(AdjustmentKindGql, {
  name: 'AdjustmentKind',
  description: 'RESTOCK/FOUND/RETURN phải tăng; DAMAGE/LOSS phải giảm; CORRECTION tuỳ chiều',
});

@InputType()
export class AdjustInventoryInput {
  @Field(() => ID) productId!: string;
  @Field(() => Int, { description: 'Số dương = nhập thêm, âm = trừ đi' }) change!: number;
  @Field(() => AdjustmentKindGql) kind!: AdjustmentKindGql;
  @Field() reason!: string;
  @Field({ nullable: true, description: 'Bỏ trống = kho mặc định' }) warehouseId?: string;
}

@ObjectType('InventoryAdjustment')
export class InventoryAdjustmentModel {
  @Field(() => Int) newQuantity!: number;
  @Field(() => StockLevelModel, { nullable: true }) stock!: StockLevelModel | null;
}

@ObjectType('Promotion')
export class PromotionModel {
  @Field(() => ID) id!: string;
  @Field() code!: string;
  @Field() type!: string;
  @Field(() => Float, { description: 'Phần trăm hoặc số tiền tuỳ type' }) value!: number;
  @Field(() => Int) usageLimit!: number;
  @Field() description!: string;
  @Field() status!: string;
  @Field(() => GraphQLISODateTime, { nullable: true }) startDate!: Date | null;
  @Field(() => GraphQLISODateTime, { nullable: true }) endDate!: Date | null;
}

@ObjectType('CustomerCartLine')
export class CustomerCartLineModel {
  @Field(() => ID) id!: number;
  @Field(() => ProductModel) product!: ProductSource;
  @Field(() => Int) quantity!: number;
  @Field(() => GraphQLISODateTime, { nullable: true }) addedAt!: Date | null;
}

@ObjectType('CustomerCart')
export class CustomerCartModel {
  @Field(() => ID) id!: number;
  @Field(() => ID) customerId!: string;
  @Field(() => [CustomerCartLineModel]) lines!: CustomerCartLineModel[];
}

@ObjectType('OrderAddress')
export class OrderAddressModel {
  @Field() fullName!: string;
  @Field() phone!: string;
  @Field() line1!: string;
  @Field(() => String, { nullable: true }) line2!: string | null;
  @Field(() => String, { nullable: true }) ward!: string | null;
  @Field(() => String, { nullable: true }) district!: string | null;
  @Field() city!: string;
  @Field() country!: string;
  @Field(() => String, { nullable: true }) postalCode!: string | null;
}

@ObjectType('OrderLine')
export class OrderLineModel {
  @Field(() => ProductModel, { nullable: true }) product!: ProductSource | null;
  @Field() name!: string;
  @Field(() => String, { nullable: true }) sku!: string | null;
  @Field(() => Int) quantity!: number;
  @Field(() => MoneyModel) unitPrice!: MoneyModel;
  @Field(() => MoneyModel) total!: MoneyModel;
}

@ObjectType('Order')
export class OrderModel {
  @Field(() => ID) orderId!: string;
  @Field() orderCode!: string;
  @Field() status!: string;
  @Field(() => [OrderLineModel]) lines!: OrderLineModel[];
  @Field(() => MoneyModel) subtotal!: MoneyModel;
  @Field(() => MoneyModel) discount!: MoneyModel;
  @Field(() => MoneyModel) total!: MoneyModel;
  @Field(() => String, { nullable: true }) promotionCode!: string | null;
  @Field(() => String, { nullable: true }) paymentMethod!: string | null;
  @Field(() => OrderAddressModel, { nullable: true }) shippingAddress!: OrderAddressModel | null;
  @Field(() => String, { nullable: true }) note!: string | null;
  @Field(() => String, { nullable: true }) cancelReason!: string | null;
  @Field(() => GraphQLISODateTime) placedAt!: Date;
  @Field(() => GraphQLISODateTime, { nullable: true }) updatedAt!: Date | null;
}
