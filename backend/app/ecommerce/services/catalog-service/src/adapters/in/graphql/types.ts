import {
  Directive,
  Field,
  GraphQLISODateTime,
  ID,
  InputType,
  Int,
  ObjectType,
  registerEnumType,
} from '@nestjs/graphql';

export enum CategoryStatusGql {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}
registerEnumType(CategoryStatusGql, { name: 'CategoryStatus' });

export enum TagStatusGql {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE',
}
registerEnumType(TagStatusGql, { name: 'TagStatus' });

@ObjectType('Category', { description: 'Danh mục sản phẩm (cây tối đa 5 cấp)' })
@Directive('@key(fields: "id")')
export class CategoryModel {
  @Field(() => ID) id!: string;
  @Field() name!: string;
  @Field() slug!: string;
  @Field(() => String, { nullable: true }) description!: string | null;
  @Field(() => String, { nullable: true }) image!: string | null;
  @Field(() => ID, { nullable: true }) parentId!: string | null;
  @Field(() => CategoryStatusGql) status!: CategoryStatusGql;
  @Field(() => Int) sortOrder!: number;
  @Field(() => Int, { description: 'Gửi kèm khi sửa để chống ghi đè (optimistic locking)' }) version!: number;
  @Field(() => GraphQLISODateTime) createdAt!: Date;
  @Field(() => GraphQLISODateTime) updatedAt!: Date;
}

@ObjectType('Brand')
@Directive('@key(fields: "id")')
export class BrandModel {
  @Field(() => Int) id!: number;
  @Field() name!: string;
  @Field() slug!: string;
  @Field(() => String, { nullable: true }) image!: string | null;
  @Field() active!: boolean;
}

@ObjectType('Tag')
@Directive('@key(fields: "id")')
export class TagModel {
  @Field(() => Int) id!: number;
  @Field() name!: string;
  @Field(() => TagStatusGql) status!: TagStatusGql;
}

@ObjectType('TagPage')
export class TagPageModel {
  @Field(() => [TagModel]) items!: TagModel[];
  @Field(() => Int) total!: number;
  @Field(() => Int) page!: number;
  @Field(() => Int) size!: number;
}

@ObjectType('Country')
@Directive('@shareable')
export class CountryModel {
  @Field(() => Int) id!: number;
  @Field({ description: 'ISO 3166-1 alpha-2' }) isoCode!: string;
  @Field(() => String, { nullable: true }) isoCode3!: string | null;
  @Field() name!: string;
  @Field(() => String, { nullable: true }) nameOrigin!: string | null;
  @Field(() => String, { nullable: true }) phoneCode!: string | null;
  @Field(() => String, { nullable: true }) currencyCode!: string | null;
  @Field(() => String, { nullable: true }) currencySymbol!: string | null;
  @Field(() => String, { nullable: true }) locale!: string | null;
  @Field(() => String, { nullable: true }) timezone!: string | null;
}

/** Product do storefront-bff sở hữu; catalog góp thêm trường `tags` (Federation entity). */
@ObjectType('Product')
@Directive('@key(fields: "id")')
export class ProductRef {
  @Field(() => ID) id!: string;
}

@InputType()
export class CreateCategoryInput {
  @Field() name!: string;
  @Field(() => String, { nullable: true }) slug?: string | null;
  @Field(() => String, { nullable: true }) description?: string | null;
  @Field(() => String, { nullable: true }) image?: string | null;
  @Field(() => ID, { nullable: true }) parentId?: string | null;
  @Field(() => Int, { nullable: true }) sortOrder?: number;
}

@InputType()
export class UpdateCategoryInput {
  @Field(() => ID) id!: string;
  @Field(() => Int) version!: number;
  @Field() name!: string;
  @Field(() => String, { nullable: true }) slug?: string | null;
  @Field(() => String, { nullable: true }) description?: string | null;
  @Field(() => String, { nullable: true }) image?: string | null;
  @Field(() => Int, { nullable: true }) sortOrder?: number;
}

@InputType()
export class MoveCategoryInput {
  @Field(() => ID) id!: string;
  @Field(() => Int) version!: number;
  @Field(() => ID, { nullable: true, description: 'null = chuyển lên gốc' }) parentId?: string | null;
}

@InputType()
export class BrandInput {
  @Field() name!: string;
  @Field(() => String, { nullable: true }) slug?: string | null;
  @Field(() => String, { nullable: true }) image?: string | null;
  @Field(() => Boolean, { nullable: true }) active?: boolean;
}
