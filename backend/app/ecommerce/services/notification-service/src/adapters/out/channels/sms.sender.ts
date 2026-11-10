import { Logger } from '@nestjs/common';
import {
  type ChannelSender,
  NoAddressError,
  PermanentDeliveryError,
  type Recipient,
} from '../../../application/ports.js';
import type { Message } from '../../../domain/notification.js';

export interface SmsOptions {
  /**
   * Webhook của nhà cung cấp SMS (ESMS, SpeedSMS, Twilio qua adapter...): POST JSON {to, text}.
   * Bỏ trống = chỉ ghi log (local/dev).
   */
  webhookUrl?: string | undefined;
  apiKey?: string | undefined;
}

/** SMS qua webhook chuẩn hoá để đổi nhà cung cấp mà không sửa code. Nội dung cắt 160 ký tự. */
export class SmsSender implements ChannelSender {
  readonly channel = 'SMS' as const;
  private readonly log = new Logger(SmsSender.name);

  constructor(private readonly opts: SmsOptions) {}

  async send(r: Recipient, m: Message) {
    if (!r.phone) throw new NoAddressError('recipient has no phone number');
    const text = `${m.title}: ${m.body}`.slice(0, 160);
    if (!this.opts.webhookUrl) {
      this.log.log(`[sms:dry-run] to=${r.phone.replace(/\d(?=\d{3})/g, '*')} ${text}`);
      return;
    }
    const res = await fetch(this.opts.webhookUrl, {
      method: 'POST',
      headers: {
        'content-type': 'application/json',
        ...(this.opts.apiKey ? { authorization: `Bearer ${this.opts.apiKey}` } : {}),
      },
      body: JSON.stringify({ to: r.phone, text }),
      signal: AbortSignal.timeout(5_000),
    });
    if (res.status >= 400 && res.status < 500) throw new PermanentDeliveryError(`sms provider rejected: ${res.status}`);
    if (!res.ok) throw new Error(`sms provider error: ${res.status}`);
  }
}
