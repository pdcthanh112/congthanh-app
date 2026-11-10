import { CurrentPrincipal, type Principal, Role, Roles } from '@ecom/platform';
import { Inject } from '@nestjs/common';
import { Args, ID, Int, Mutation, Query, Resolver, Subscription } from '@nestjs/graphql';
import { v7 as uuidv7 } from 'uuid';
import { NotificationService } from '../../../application/notification.service.js';
import { REALTIME, type RealtimePublisher } from '../../../application/ports.js';
import { CATEGORIES, CHANNELS, type Notification, TRANSACTIONAL } from '../../../domain/notification.js';
import { keyOf, type Preferences } from '../../../domain/preferences.js';
import {
  type CategoryGql,
  type ChannelGql,
  NotificationModel,
  NotificationPageModel,
  PreferencesModel,
  QuietHoursInput,
  SendNotificationInput,
  SettingInput,
  WebPushInput,
} from './types.js';

const toModel = (n: Notification): NotificationModel => ({ ...n, category: n.category as CategoryGql });

function toPreferencesModel(p: Preferences): PreferencesModel {
  return {
    quietHours: p.quietHours,
    settings: CATEGORIES.flatMap((category) =>
      CHANNELS.map((channel) => {
        const locked = TRANSACTIONAL.has(category) && (channel === 'IN_APP' || channel === 'EMAIL');
        return {
          category: category as CategoryGql,
          channel: channel as ChannelGql,
          enabled: locked || !p.disabled.has(keyOf(category, channel)),
          editable: !locked,
        };
      }),
    ),
  };
}

/** Qua JSON (Valkey) Date thành chuỗi: dựng lại để scalar DateTime serialize đúng. */
@Resolver(() => NotificationModel)
export class NotificationResolver {
  constructor(
    private readonly notifications: NotificationService,
    @Inject(REALTIME) private readonly realtime: RealtimePublisher,
  ) {}

  @Query(() => NotificationPageModel)
  async myNotifications(
    @CurrentPrincipal() p: Principal,
    @Args('unreadOnly', { defaultValue: false }) unreadOnly: boolean,
    @Args('page', { type: () => Int, defaultValue: 1 }) page: number,
    @Args('size', { type: () => Int, defaultValue: 20 }) size: number,
  ): Promise<NotificationPageModel> {
    const res = await this.notifications.list(p.id, unreadOnly, page, size);
    return { total: res.total, items: res.items.map(toModel) };
  }

  @Query(() => Int)
  unreadNotificationCount(@CurrentPrincipal() p: Principal) {
    return this.notifications.read.notifications.unreadCount(p.id);
  }

  @Query(() => PreferencesModel)
  async myNotificationPreferences(@CurrentPrincipal() p: Principal) {
    return toPreferencesModel(await this.notifications.read.preferences.get(p.id));
  }

  @Mutation(() => Int, { description: 'Số thông báo vừa đánh dấu đã đọc' })
  markNotificationsRead(
    @CurrentPrincipal() p: Principal,
    @Args('ids', { type: () => [ID], nullable: true, description: 'Bỏ trống = tất cả' }) ids?: string[] | null,
  ) {
    return this.notifications.markRead(p.id, ids ?? 'ALL');
  }

  @Mutation(() => PreferencesModel)
  async updateNotificationPreferences(
    @CurrentPrincipal() p: Principal,
    @Args('settings', { type: () => [SettingInput], defaultValue: [] }) settings: SettingInput[],
    @Args('quietHours', { type: () => QuietHoursInput, nullable: true }) quietHours?: QuietHoursInput | null,
    @Args('clearQuietHours', { defaultValue: false }) clearQuietHours?: boolean,
  ) {
    const qh = clearQuietHours ? null : (quietHours ?? undefined);
    return toPreferencesModel(await this.notifications.updatePreferences(p.id, settings, qh));
  }

  @Mutation(() => Boolean)
  async registerWebPush(@CurrentPrincipal() p: Principal, @Args('subscription') s: WebPushInput) {
    await this.notifications.registerWebPush(p.id, s);
    return true;
  }

  @Mutation(() => Boolean)
  async registerFcmToken(
    @CurrentPrincipal() p: Principal,
    @Args('token') token: string,
    @Args('platform', { defaultValue: 'android' }) platform: string,
  ) {
    await this.notifications.registerFcmToken(p.id, token, platform);
    return true;
  }

  @Mutation(() => Boolean, { description: 'Gỡ thiết bị khi đăng xuất' })
  async unregisterDevice(
    @Args('fcmToken', { nullable: true }) fcmToken?: string,
    @Args('webPushEndpoint', { nullable: true }) endpoint?: string,
  ) {
    if (fcmToken) await this.notifications.read.devices.removeFcmToken(fcmToken);
    if (endpoint) await this.notifications.read.devices.removeWebPush(endpoint);
    return true;
  }

  @Mutation(() => NotificationModel, { nullable: true, description: 'CSKH/Admin gửi thông báo cho một người dùng' })
  @Roles(Role.ADMIN, Role.SUPPORT)
  async sendNotification(@CurrentPrincipal() p: Principal, @Args('input') i: SendNotificationInput) {
    const n = await this.notifications.send({
      userId: i.userId,
      category: 'SYSTEM',
      type: 'MANUAL',
      message: { title: i.title, body: i.body, link: i.link ?? null },
      dedupKey: `manual:${p.id}:${uuidv7()}`,
      channels: i.channels,
    });
    return n ? toModel(n) : null;
  }

  @Subscription(() => NotificationModel, {
    description: 'Thông báo mới của người đang đăng nhập (graphql-ws qua router)',
    resolve: (n: Notification) => toModel(n),
  })
  notificationAdded(@CurrentPrincipal() p: Principal) {
    return this.realtime.stream(p.id);
  }
}
