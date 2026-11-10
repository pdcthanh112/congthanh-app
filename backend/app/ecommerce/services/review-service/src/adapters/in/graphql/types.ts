import { Directive, Field, Float, ID, InputType, Int, ObjectType, registerEnumType } from '@nestjs/graphql';

export enum ReviewStatusGql {
  PUBLISHED = 'PUBLISHED',
  HIDDEN = 'HIDDEN',
}
export enum ReviewSortGql {
  NEWEST = 'NEWEST',
  HELPFUL = 'HELPFUL',
  RATING_HIGH = 'RATING_HIGH',
  RATING_LOW = 'RATING_LOW',
}
registerEnumType(ReviewStatusGql, { name: 'ReviewStatus' });
registerEnumType(ReviewSortGql, { name: 'ReviewSort' });

@ObjectType('Product')
@Directive('@key(fields: "id")')
export class ProductRef {
  @Field(() => ID) id!: string;
}

@ObjectType('Review')
@Directive('@key(fields: "id")')
export class ReviewModel {
  @Field(() => ID) id!: string;
  @Field(() => ProductRef) product!: ProductRef;
  @Field({ description: 'Tên người viết (đã che bớt với người khác)' }) authorName!: string;
  @Field(() => Int) rating!: number;
  @Field(() => String, { nullable: true }) title!: string | null;
  @Field(() => String, { nullable: true }) content!: string | null;
  @Field(() => [String]) mediaUrls!: string[];
  @Field({ description: 'Luôn true: chỉ người đã mua mới viết được' }) verifiedPurchase!: boolean;
  @Field(() => Int) helpfulCount!: number;
  @Field(() => String, { nullable: true }) sellerReply!: string | null;
  @Field(() => Date, { nullable: true }) repliedAt!: Date | null;
  @Field(() => ReviewStatusGql) status!: ReviewStatusGql;
  @Field(() => String, { nullable: true, description: 'Chỉ tác giả và admin thấy' }) moderationReason!: string | null;
  @Field() createdAt!: Date;
  @Field() updatedAt!: Date;
  @Field(() => Int) version!: number;
}

@ObjectType('ReviewPage')
export class ReviewPageModel {
  @Field(() => [ReviewModel]) items!: ReviewModel[];
  @Field(() => Int) total!: number;
}

@ObjectType('RatingSummary')
export class RatingSummaryModel {
  @Field(() => Float) average!: number;
  @Field(() => Int) count!: number;
  @Field(() => [Int], { description: 'Số lượng theo sao, phần tử 0 = 1 sao ... 4 = 5 sao' }) stars!: number[];
}

@ObjectType('PendingReview', { description: 'Sản phẩm đã mua chưa đánh giá' })
export class PendingReviewModel {
  @Field(() => ID) orderId!: string;
  @Field(() => ProductRef) product!: ProductRef;
  @Field(() => Date, { nullable: true }) purchasedAt!: Date | null;
}

@InputType()
export class SubmitReviewInput {
  @Field(() => ID) orderId!: string;
  @Field(() => ID) productId!: string;
  @Field(() => Int) rating!: number;
  @Field(() => String, { nullable: true }) title?: string | null;
  @Field(() => String, { nullable: true }) content?: string | null;
  @Field(() => [String], { defaultValue: [] }) mediaUrls!: string[];
}

@InputType()
export class EditReviewInput {
  @Field(() => Int) rating!: number;
  @Field(() => String, { nullable: true }) title?: string | null;
  @Field(() => String, { nullable: true }) content?: string | null;
  @Field(() => [String], { defaultValue: [] }) mediaUrls!: string[];
}
