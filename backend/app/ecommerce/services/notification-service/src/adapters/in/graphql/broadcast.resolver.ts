import { CurrentPrincipal, type Principal, Role, Roles } from '@ecommerce/platform';
import { Args, ID, Int, Mutation, Parent, Query, ResolveField, Resolver } from '@nestjs/graphql';
import { BroadcastService } from '../../../application/broadcast.service.js';
import type { Broadcast, BroadcastAudience } from '../../../domain/broadcast.js';
import type { Category } from '../../../domain/notification.js';
import {
  type BroadcastAudienceInput,
  BroadcastAudienceKindGql,
  BroadcastChannelProgressModel,
  BroadcastModel,
  BroadcastPageModel,
  type BroadcastStatusGql,
  type CategoryGql,
  type ChannelGql,
  CreateBroadcastInput,
} from './types.js';

const toModel = (b: Broadcast): BroadcastModel => ({
  ...b,
  category: b.category as CategoryGql,
  channels: b.channels as ChannelGql[],
  status: b.status as BroadcastStatusGql,
  audienceKind: b.audience.kind as BroadcastAudienceKindGql,
  audienceRole: b.audience.kind === 'ROLE' ? b.audience.role : null,
  audienceScanned: b.audienceCursor,
});

function toAudience(i: BroadcastAudienceInput | null | undefined): BroadcastAudience {
  if (i?.kind === BroadcastAudienceKindGql.ROLE) return { kind: 'ROLE', role: i.role ?? '' };
  return { kind: 'ALL_USERS' };
}

/** Admin gửi email/thông báo hàng loạt: gửi ngay hoặc hẹn giờ, huỷ, xem tiến độ (ADR 0034). */
@Resolver(() => BroadcastModel)
@Roles(Role.ADMIN)
export class BroadcastResolver {
  constructor(private readonly service: BroadcastService) {}

  @Mutation(() => BroadcastModel, {
    description: 'Tạo chiến dịch gửi hàng loạt; gọi lại cùng idempotencyKey trả chiến dịch đã tạo',
  })
  async createBroadcast(
    @CurrentPrincipal() p: Principal,
    @Args('input') i: CreateBroadcastInput,
    @Args('idempotencyKey') idempotencyKey: string,
  ) {
    const b = await this.service.create(
      {
        category: i.category as Category,
        title: i.title,
        body: i.body,
        link: i.link ?? null,
        channels: i.channels,
        audience: toAudience(i.audience),
        scheduledAt: i.scheduledAt ?? null,
      },
      p.id,
      idempotencyKey,
    );
    return toModel(b);
  }

  @Mutation(() => BroadcastModel, { description: 'Huỷ chiến dịch chưa xong; lượt gửi còn chờ bị bỏ' })
  async cancelBroadcast(@Args('id', { type: () => ID }) id: string) {
    return toModel(await this.service.cancel(id));
  }

  @Query(() => BroadcastModel)
  async broadcast(@Args('id', { type: () => ID }) id: string) {
    return toModel(await this.service.get(id));
  }

  @Query(() => BroadcastPageModel)
  async broadcasts(
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 20 }) size: number,
  ): Promise<BroadcastPageModel> {
    const res = await this.service.list(page, size);
    return { total: res.total, items: res.items.map(toModel) };
  }

  @ResolveField(() => [BroadcastChannelProgressModel], { description: 'Số lượt gửi theo kênh và trạng thái' })
  async progress(@Parent() b: BroadcastModel): Promise<BroadcastChannelProgressModel[]> {
    const progressByChannel = new Map<string, BroadcastChannelProgressModel>();
    for (const c of await this.service.deliveryCounts(b.id)) {
      const row = progressByChannel.get(c.channel) ?? {
        channel: c.channel as ChannelGql,
        pending: 0,
        sent: 0,
        failed: 0,
        skipped: 0,
      };
      if (c.status === 'PENDING') row.pending = c.count;
      if (c.status === 'SENT') row.sent = c.count;
      if (c.status === 'FAILED') row.failed = c.count;
      if (c.status === 'SKIPPED') row.skipped = c.count;
      progressByChannel.set(c.channel, row);
    }
    return [...progressByChannel.values()];
  }
}
