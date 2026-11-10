import { type App, cert, initializeApp } from 'firebase-admin/app';
import { getMessaging, type Messaging } from 'firebase-admin/messaging';
import {
  type ChannelSender,
  type DeviceRepository,
  NoAddressError,
  type Recipient,
} from '../../../application/ports.js';
import type { Message } from '../../../domain/notification.js';

/** FCM HTTP v1 qua firebase-admin cho app di động. `serviceAccountJson` lấy từ secret (External Secrets). */
export class FcmSender implements ChannelSender {
  readonly channel = 'FCM' as const;
  private readonly messaging: Messaging;

  constructor(serviceAccountJson: string) {
    const app: App = initializeApp({ credential: cert(JSON.parse(serviceAccountJson)) }, 'notification-service');
    this.messaging = getMessaging(app);
  }

  async send(r: Recipient, m: Message, devices: DeviceRepository) {
    const tokens = await devices.fcmTokensOf(r.id);
    if (tokens.length === 0) throw new NoAddressError('no FCM token');
    const res = await this.messaging.sendEachForMulticast({
      tokens,
      notification: { title: m.title, body: m.body },
      data: m.link ? { link: m.link } : {},
      android: { priority: 'high' },
      apns: { payload: { aps: { sound: 'default' } } },
    });
    await Promise.all(
      res.responses.map((x, i) =>
        x.error?.code === 'messaging/registration-token-not-registered' ||
        x.error?.code === 'messaging/invalid-registration-token'
          ? devices.removeFcmToken(tokens[i]!)
          : undefined,
      ),
    );
    if (res.successCount === 0 && res.responses.some((x) => x.error && !x.error.code.includes('token'))) {
      throw new Error(`fcm failed: ${res.responses[0]?.error?.message}`);
    }
  }
}
