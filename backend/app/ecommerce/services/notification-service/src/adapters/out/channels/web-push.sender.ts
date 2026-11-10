import webpush from 'web-push';
import {
  type ChannelSender,
  type DeviceRepository,
  NoAddressError,
  type Recipient,
} from '../../../application/ports.js';
import type { Message } from '../../../domain/notification.js';

export interface VapidOptions {
  subject: string;
  publicKey: string;
  privateKey: string;
}

/** Web Push (VAPID) tới mọi trình duyệt người dùng đã đăng ký; subscription hết hạn (404/410) bị xoá. */
export class WebPushSender implements ChannelSender {
  readonly channel = 'WEB_PUSH' as const;

  constructor(private readonly vapid: VapidOptions) {}

  async send(r: Recipient, m: Message, devices: DeviceRepository) {
    const subs = await devices.webPushOf(r.id);
    if (subs.length === 0) throw new NoAddressError('no web push subscription');
    const payload = JSON.stringify({ title: m.title, body: m.body, url: m.link });
    const results = await Promise.allSettled(
      subs.map((s) =>
        webpush.sendNotification({ endpoint: s.endpoint, keys: { p256dh: s.p256dh, auth: s.auth } }, payload, {
          vapidDetails: this.vapid,
          TTL: 86_400,
          timeout: 5_000,
        }),
      ),
    );
    let delivered = 0;
    for (const [i, res] of results.entries()) {
      if (res.status === 'fulfilled') delivered++;
      else if ([404, 410].includes((res.reason as { statusCode?: number }).statusCode ?? 0))
        await devices.removeWebPush(subs[i]!.endpoint);
    }
    if (
      delivered === 0 &&
      results.some(
        (r) => r.status === 'rejected' && ![404, 410].includes((r.reason as { statusCode?: number }).statusCode ?? 0),
      )
    ) {
      throw new Error('web push failed for all subscriptions');
    }
  }
}
