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

/** Tiền: amount là số thập phân theo đơn vị tiền (VND không có phần lẻ); formatted để hiển thị ngay. */
@ObjectType('Money')
@Directive('@shareable')
export class MoneyModel {
  @Field(() => Float) amount!: number;
  @Field({ description: 'ISO 4217' }) currency!: string;
  @Field({ description: 'Đã định dạng theo vi-VN, ví dụ "1.250.000 ₫"' }) formatted!: string;
}

export enum StockStatusGql {
  IN_STOCK = 'IN_STOCK',
  LOW_STOCK = 'LOW_STOCK',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  BACKORDER = 'BACKORDER',
  DISCONTINUED = 'DISCONTINUED',
}
registerEnumType(StockStatusGql, { name: 'StockStatus' });

@ObjectType('Stock')
export class StockModel {
  @Field(() => StockStatusGql) status!: StockStatusGql;
  @Field(() => Int, { description: 'Số lượng còn bán được (đã trừ hàng đang giữ)' }) available!: number;
}

/** Category do catalog-service sở hữu; ở đây chỉ trả id để router lấy phần còn lại. */
@ObjectType('Category')
@Directive('@key(fields: "id", resolvable: false)')
export class CategoryRef {
  @Field(() => ID) id!: string;
}

/**
 * Nguồn của Product trong resolver: luôn có id; summary (gRPC, theo lô) và detail (REST) được nạp
 * lười qua DataLoader khi truy vấn thực sự cần trường đó.
 */
export interface ProductSource {
  id: string;
}

@ObjectType('Product', { description: 'Sản phẩm (dữ liệu gốc ở product-service)' })
@Directive('@key(fields: "id")')
export class ProductModel {
  @Field(() => ID) id!: string;
  @Field() sku!: string;
  @Field() name!: string;
  @Field(() => String, { nullable: true }) slug!: string | null;
  @Field(() => String, { nullable: true }) description!: string | null;
  @Field(() => String, { nullable: true }) thumbnail!: string | null;
  @Field(() => [String]) images!: string[];
  @Field(() => String, { nullable: true }) brandName!: string | null;
  @Field(() => MoneyModel) price!: MoneyModel;
  @Field(() => MoneyModel) originalPrice!: MoneyModel;
  @Field(() => Int, { description: 'Phần trăm giảm so với giá gốc (0 nếu không giảm)' }) discountPercent!: number;
  @Field() active!: boolean;
  @Field(() => ID) sellerId!: string;
  @Field(() => [CategoryRef]) categories!: CategoryRef[];
  @Field(() => StockModel, { nullable: true, description: 'null khi inventory-service tạm không trả lời' })
  stock!: StockModel | null;
}

@ObjectType('ProductPage')
export class ProductPageModel {
  @Field(() => [ProductModel]) items!: ProductSource[];
  @Field(() => Int) page!: number;
  @Field(() => Int) limit!: number;
  @Field(() => Int) totalPages!: number;
  @Field(() => Int) totalItems!: number;
}

export enum CartLineIssueGql {
  UNAVAILABLE = 'UNAVAILABLE',
  OUT_OF_STOCK = 'OUT_OF_STOCK',
  INSUFFICIENT_STOCK = 'INSUFFICIENT_STOCK',
}
registerEnumType(CartLineIssueGql, { name: 'CartLineIssue' });

@ObjectType('CartLine')
export class CartLineModel {
  @Field(() => ID) id!: number;
  @Field(() => ProductModel, { nullable: true, description: 'null nếu sản phẩm đã bị xoá' })
  product!: ProductSource | null;
  @Field(() => Int) quantity!: number;
  @Field(() => MoneyModel, { nullable: true }) unitPrice!: MoneyModel | null;
  @Field(() => MoneyModel, { nullable: true }) lineTotal!: MoneyModel | null;
  @Field() purchasable!: boolean;
  @Field(() => CartLineIssueGql, { nullable: true }) issue!: CartLineIssueGql | null;
  @Field(() => GraphQLISODateTime, { nullable: true }) addedAt!: Date | null;
}

@ObjectType('Cart')
export class CartModel {
  @Field(() => ID, { nullable: true, description: 'null khi khách chưa có giỏ' }) id!: number | null;
  @Field(() => [CartLineModel]) lines!: CartLineModel[];
  @Field(() => MoneyModel, { description: 'Tạm tính các dòng mua được' }) subtotal!: MoneyModel;
  @Field(() => Int) itemCount!: number;
  @Field({ description: 'Mọi dòng đều mua được và giỏ không rỗng' }) checkoutReady!: boolean;
}

@ObjectType('PromotionQuote')
export class PromotionQuoteModel {
  @Field() valid!: boolean;
  @Field(() => String, { nullable: true }) reason!: string | null;
  @Field(() => MoneyModel) subtotal!: MoneyModel;
  @Field(() => MoneyModel, { nullable: true }) discount!: MoneyModel | null;
  @Field(() => MoneyModel) total!: MoneyModel;
}

export enum PaymentMethodGql {
  CASH_ON_DELIVERY = 'CASH_ON_DELIVERY',
  CREDIT_CARD = 'CREDIT_CARD',
  STRIPE = 'STRIPE',
  PAYPAL = 'PAYPAL',
  VN_PAY = 'VN_PAY',
  MOMO = 'MOMO',
}
registerEnumType(PaymentMethodGql, { name: 'PaymentMethod' });

@InputType()
export class CheckoutInput {
  @Field({ description: 'Sinh một lần cho mỗi lần bấm "Đặt hàng" (16-64 ký tự); gửi lại không tạo đơn mới' })
  idempotencyKey!: string;
  @Field(() => PaymentMethodGql) paymentMethod!: PaymentMethodGql;
  @Field(() => ID, { nullable: true, description: 'Bỏ trống = địa chỉ mặc định' }) shippingAddressId?: string;
  @Field({ nullable: true }) promotionCode?: string;
  @Field({ nullable: true, description: 'Token thẻ từ SDK cổng thanh toán (không gửi số thẻ)' })
  paymentToken?: string;
  @Field({ nullable: true, description: 'Bắt buộc với thanh toán online: trang quay về sau khi trả tiền' })
  returnUrl?: string;
  @Field({ nullable: true }) note?: string;
}

@ObjectType('CheckoutResult')
export class CheckoutResultModel {
  @Field(() => ID) orderId!: string;
  @Field() orderCode!: string;
  @Field() status!: string;
  @Field(() => MoneyModel) total!: MoneyModel;
  @Field(() => MoneyModel) discount!: MoneyModel;
  @Field({ description: 'Theo dõi tiến trình bằng checkoutProgress(orderCode)' }) checkoutUrl!: string;
}

@ObjectType('CheckoutProgress')
export class CheckoutProgressModel {
  @Field() orderCode!: string;
  @Field() orderStatus!: string;
  @Field(() => String, { nullable: true }) step!: string | null;
  @Field(() => String, { nullable: true, description: 'Có khi cần chuyển khách sang cổng thanh toán' })
  paymentRedirectUrl!: string | null;
  @Field(() => String, { nullable: true }) failureReason!: string | null;
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
  @Field(() => ProductModel, { nullable: true, description: 'null nếu sản phẩm đã bị xoá' })
  product!: ProductSource | null;
  @Field({ description: 'Tên tại thời điểm đặt' }) name!: string;
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

@ObjectType('OrderSummary')
export class OrderSummaryModel {
  @Field() orderCode!: string;
  @Field() status!: string;
  @Field(() => MoneyModel) total!: MoneyModel;
  @Field(() => Int) itemCount!: number;
  @Field(() => GraphQLISODateTime) placedAt!: Date;
}
