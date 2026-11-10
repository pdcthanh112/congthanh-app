import nodemailer, { type Transporter } from 'nodemailer';
import {
  type ChannelSender,
  NoAddressError,
  PermanentDeliveryError,
  type Recipient,
} from '../../../application/ports.js';
import type { Message } from '../../../domain/notification.js';
import { emailHtml } from '../../../domain/templates.js';

export interface SmtpOptions {
  /** smtp://user:pass@host:587 (local: Mailpit smtp://localhost:1025) */
  url: string;
  from: string;
  appUrl: string;
}

export class EmailSender implements ChannelSender {
  readonly channel = 'EMAIL' as const;
  private readonly transport: Transporter;

  constructor(private readonly opts: SmtpOptions) {
    this.transport = nodemailer.createTransport(opts.url);
  }

  async send(r: Recipient, m: Message) {
    if (!r.email) throw new NoAddressError('recipient has no verified email');
    try {
      await this.transport.sendMail({
        from: this.opts.from,
        to: r.name ? { name: r.name, address: r.email } : r.email,
        subject: m.title,
        text: `${m.body}${m.link ? `\n\n${this.opts.appUrl}${m.link}` : ''}`,
        html: m.html ?? emailHtml(m, this.opts.appUrl),
      });
    } catch (e) {
      const code = (e as { responseCode?: number }).responseCode;
      // 5xx từ SMTP (hộp thư không tồn tại...) là lỗi vĩnh viễn; còn lại thử lại.
      if (code && code >= 500) throw new PermanentDeliveryError(`smtp ${code}: ${(e as Error).message}`);
      throw e;
    }
  }
}
