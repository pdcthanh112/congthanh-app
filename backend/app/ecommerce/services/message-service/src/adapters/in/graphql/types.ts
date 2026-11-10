import { Directive, Field, ID, InputType, Int, ObjectType, registerEnumType } from '@nestjs/graphql';

export enum ConversationKindGql {
  SELLER = 'SELLER',
  SUPPORT = 'SUPPORT',
}
registerEnumType(ConversationKindGql, { name: 'ConversationKind' });

@ObjectType('Product')
@Directive('@key(fields: "id")')
export class ProductRef {
  @Field(() => ID) id!: string;
}

@ObjectType('ChatMessage')
export class ChatMessageModel {
  @Field(() => ID) id!: string;
  @Field(() => ID) conversationId!: string;
  @Field(() => Int, { description: 'Số thứ tự tăng dần trong hội thoại (dùng cho phân trang và đánh dấu đã đọc)' })
  seq!: number;
  @Field(() => ID) senderId!: string;
  @Field() body!: string;
  @Field(() => [String]) attachments!: string[];
  @Field(() => String, { nullable: true }) clientMessageId!: string | null;
  @Field() createdAt!: Date;
}

@ObjectType('Conversation')
export class ConversationModel {
  @Field(() => ID) id!: string;
  @Field(() => ConversationKindGql) kind!: ConversationKindGql;
  @Field(() => ID) customerId!: string;
  @Field(() => ID, { description: 'Id người bán, hoặc "support"' }) counterpartId!: string;
  @Field(() => ProductRef, { nullable: true }) product!: ProductRef | null;
  @Field(() => ID, { nullable: true }) orderId!: string | null;
  @Field(() => Int) lastSeq!: number;
  @Field(() => Date, { nullable: true }) lastMessageAt!: Date | null;
  @Field(() => String, { nullable: true }) lastMessagePreview!: string | null;
  @Field(() => Int, { description: 'Số tin chưa đọc của người đang xem' }) unread!: number;
  @Field() createdAt!: Date;
}

@ObjectType('ConversationPage')
export class ConversationPageModel {
  @Field(() => [ConversationModel]) items!: ConversationModel[];
  @Field(() => Int) total!: number;
}

@ObjectType('StartConversationResult')
export class StartResultModel {
  @Field(() => ConversationModel) conversation!: ConversationModel;
  @Field(() => ChatMessageModel) message!: ChatMessageModel;
}

@ObjectType('ChatEvent', { description: 'Sự kiện realtime: tin mới (message) hoặc đang gõ (typingUserId)' })
export class ChatEventModel {
  @Field(() => ID) conversationId!: string;
  @Field(() => ChatMessageModel, { nullable: true }) message!: ChatMessageModel | null;
  @Field(() => ID, { nullable: true }) typingUserId!: string | null;
}

@InputType()
export class MessageInput {
  @Field({ defaultValue: '' }) body!: string;
  @Field(() => [String], { defaultValue: [] }) attachments!: string[];
  @Field(() => String, { nullable: true, description: 'Id do client sinh (uuid) để gửi lại không bị trùng' })
  clientMessageId?: string | null;
}
