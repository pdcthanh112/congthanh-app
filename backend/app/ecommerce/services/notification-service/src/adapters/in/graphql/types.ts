import { Field, ID, InputType, Int, ObjectType, registerEnumType } from '@nestjs/graphql';

export enum ChannelGql {
  IN_APP = 'IN_APP',
  EMAIL = 'EMAIL',
  SMS = 'SMS',
  WEB_PUSH = 'WEB_PUSH',
  FCM = 'FCM',
}
export enum CategoryGql {
  ORDER = 'ORDER',
  PAYMENT = 'PAYMENT',
  REVIEW = 'REVIEW',
  WISHLIST = 'WISHLIST',
  PROMOTION = 'PROMOTION',
  MESSAGE = 'MESSAGE',
  SYSTEM = 'SYSTEM',
  INVENTORY = 'INVENTORY',
}
registerEnumType(ChannelGql, { name: 'NotificationChannel' });
registerEnumType(CategoryGql, { name: 'NotificationCategory' });

@ObjectType('Notification')
export class NotificationModel {
  @Field(() => ID) id!: string;
  @Field(() => CategoryGql) category!: CategoryGql;
  @Field() type!: string;
  @Field() title!: string;
  @Field() body!: string;
  @Field(() => String, { nullable: true }) link!: string | null;
  @Field(() => Date, { nullable: true }) readAt!: Date | null;
  @Field() createdAt!: Date;
}

@ObjectType('NotificationPage')
export class NotificationPageModel {
  @Field(() => [NotificationModel]) items!: NotificationModel[];
  @Field(() => Int) total!: number;
}

@ObjectType('QuietHours')
export class QuietHoursModel {
  @Field() start!: string;
  @Field() end!: string;
  @Field() timezone!: string;
}

@ObjectType('NotificationSetting')
export class SettingModel {
  @Field(() => CategoryGql) category!: CategoryGql;
  @Field(() => ChannelGql) channel!: ChannelGql;
  @Field() enabled!: boolean;
  @Field({ description: 'false với nhóm giao dịch (đơn hàng, thanh toán) qua in-app/email' }) editable!: boolean;
}

@ObjectType('NotificationPreferences')
export class PreferencesModel {
  @Field(() => [SettingModel]) settings!: SettingModel[];
  @Field(() => QuietHoursModel, { nullable: true }) quietHours!: QuietHoursModel | null;
}

@InputType()
export class SettingInput {
  @Field(() => CategoryGql) category!: CategoryGql;
  @Field(() => ChannelGql) channel!: ChannelGql;
  @Field() enabled!: boolean;
}

@InputType()
export class QuietHoursInput {
  @Field({ description: 'HH:mm' }) start!: string;
  @Field({ description: 'HH:mm, có thể qua ngày' }) end!: string;
  @Field({ defaultValue: 'America/New_York' }) timezone!: string;
}

@InputType()
export class WebPushInput {
  @Field() endpoint!: string;
  @Field() p256dh!: string;
  @Field() auth!: string;
}

@InputType()
export class SendNotificationInput {
  @Field(() => ID) userId!: string;
  @Field() title!: string;
  @Field() body!: string;
  @Field(() => String, { nullable: true }) link?: string | null;
  @Field(() => [ChannelGql], { defaultValue: [ChannelGql.IN_APP] }) channels!: ChannelGql[];
}
