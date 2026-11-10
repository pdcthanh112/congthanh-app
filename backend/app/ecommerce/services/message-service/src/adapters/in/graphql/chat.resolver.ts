import { CurrentPrincipal, hasAnyRole, type Principal, Role } from '@ecom/platform';
import { Inject } from '@nestjs/common';
import { Args, ID, Int, Mutation, Query, Resolver, Subscription } from '@nestjs/graphql';
import { ChatService } from '../../../application/chat.service.js';
import { type ConversationSummary, REALTIME, type Realtime, type RealtimeEvent } from '../../../application/ports.js';
import type { ChatMessage, Conversation, Viewer } from '../../../domain/conversation.js';
import {
  ChatEventModel,
  ChatMessageModel,
  type ConversationKindGql,
  ConversationModel,
  ConversationPageModel,
  MessageInput,
  StartResultModel,
} from './types.js';

const viewerOf = (p: Principal): Viewer => ({ id: p.id, isSupport: hasAnyRole(p, [Role.SUPPORT, Role.ADMIN]) });
const toConversation = (c: Conversation, unread = 0): ConversationModel => ({
  ...c,
  kind: c.kind as ConversationKindGql,
  product: c.productId ? { id: c.productId } : null,
  unread,
});
const reviveMessage = (m: ChatMessage): ChatMessage => ({ ...m, createdAt: new Date(m.createdAt) });

@Resolver(() => ConversationModel)
export class ChatResolver {
  constructor(
    private readonly chat: ChatService,
    @Inject(REALTIME) private readonly realtime: Realtime,
  ) {}

  @Query(() => ConversationPageModel, { description: 'Hội thoại của tôi (nhân viên hỗ trợ thấy cả hàng chờ hỗ trợ)' })
  async myConversations(
    @CurrentPrincipal() p: Principal,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 20 }) size: number,
  ): Promise<ConversationPageModel> {
    const res = await this.chat.list(viewerOf(p), page, size);
    return { total: res.total, items: res.items.map((c: ConversationSummary) => toConversation(c, c.unread)) };
  }

  @Query(() => ConversationModel)
  async conversation(@CurrentPrincipal() p: Principal, @Args('id', { type: () => ID }) id: string) {
    return toConversation(await this.chat.conversation(viewerOf(p), id));
  }

  @Query(() => [ChatMessageModel], { description: 'Tin nhắn, mới nhất trước; cuộn lên bằng beforeSeq' })
  messages(
    @CurrentPrincipal() p: Principal,
    @Args('conversationId', { type: () => ID }) conversationId: string,
    @Args('beforeSeq', { type: () => Int, nullable: true }) beforeSeq: number | null,
    @Args('limit', { type: () => Int, defaultValue: 30 }) limit: number,
  ) {
    return this.chat.history(viewerOf(p), conversationId, beforeSeq, limit);
  }

  @Query(() => Int)
  unreadMessageCount(@CurrentPrincipal() p: Principal) {
    return this.chat.read.conversations.unreadTotal(p.id);
  }

  @Mutation(() => StartResultModel, {
    description: 'Hỏi người bán về sản phẩm (mở hội thoại hoặc dùng lại hội thoại cũ)',
  })
  async contactSeller(
    @CurrentPrincipal() p: Principal,
    @Args('productId', { type: () => ID }) productId: string,
    @Args('message') message: MessageInput,
  ) {
    const r = await this.chat.startWithSeller(viewerOf(p), productId, message);
    return { conversation: toConversation(r.conversation), message: r.message };
  }

  @Mutation(() => StartResultModel)
  async contactSupport(
    @CurrentPrincipal() p: Principal,
    @Args('message') message: MessageInput,
    @Args('orderId', { type: () => ID, nullable: true }) orderId: string | null,
  ) {
    const r = await this.chat.startSupport(viewerOf(p), orderId, message);
    return { conversation: toConversation(r.conversation), message: r.message };
  }

  @Mutation(() => ChatMessageModel)
  sendMessage(
    @CurrentPrincipal() p: Principal,
    @Args('conversationId', { type: () => ID }) conversationId: string,
    @Args('message') message: MessageInput,
  ) {
    return this.chat.send(viewerOf(p), conversationId, message);
  }

  @Mutation(() => Boolean)
  async markConversationRead(
    @CurrentPrincipal() p: Principal,
    @Args('conversationId', { type: () => ID }) conversationId: string,
    @Args('seq', { type: () => Int }) seq: number,
  ) {
    await this.chat.markRead(viewerOf(p), conversationId, seq);
    return true;
  }

  @Mutation(() => Boolean, { description: 'Báo "đang gõ" (không lưu, chỉ đẩy realtime)' })
  async sendTyping(
    @CurrentPrincipal() p: Principal,
    @Args('conversationId', { type: () => ID }) conversationId: string,
  ) {
    await this.chat.typing(viewerOf(p), conversationId);
    return true;
  }

  @Subscription(() => ChatEventModel, {
    description: 'Tin mới và trạng thái đang gõ trong mọi hội thoại của tôi',
    resolve: (e: RealtimeEvent): ChatEventModel =>
      e.kind === 'message'
        ? { conversationId: e.conversationId, message: reviveMessage(e.message), typingUserId: null }
        : { conversationId: e.conversationId, message: null, typingUserId: e.userId },
    filter: (e: RealtimeEvent, _args: unknown, ctx: { req?: { principal?: Principal } }) =>
      !(e.kind === 'typing' && e.userId === ctx.req?.principal?.id),
  })
  chatEvents(@CurrentPrincipal() p: Principal) {
    return this.realtime.stream(p.id, viewerOf(p).isSupport);
  }
}
