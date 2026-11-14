import { z } from 'zod';
import { RestClient } from './rest-client.js';

export type ModerationContentType = 'REVIEW' | 'MESSAGE' | 'PRODUCT_LISTING' | 'SUPPLIER_PROFILE';
export type ModerationDecision = 'APPROVE' | 'REJECT' | 'REDACT' | 'ESCALATE';

export interface ModerationVerdict {
  decision: ModerationDecision;
  redactedText: string;
  categories: string[];
}

const Verdict = z.object({
  decision: z.enum(['APPROVE', 'REJECT', 'REDACT', 'ESCALATE']),
  redactedText: z.string().nullish(),
  categories: z.array(z.string()).nullish(),
});

/**
 * Client tới moderation-service (POST /internal/moderation/items, chỉ gọi trong mesh), ADR 0026/0027.
 * Lỗi/timeout: trả null để service gọi tự quyết (review: đăng như cũ và ghi log; chat: gửi như cũ).
 */
export class ModerationClient {
  private readonly http: RestClient;

  constructor(baseUrl: string, timeoutMs = 3_000) {
    this.http = new RestClient({ baseUrl, name: 'moderation-service', timeoutMs, retries: 0 });
  }

  async check(input: {
    contentType: ModerationContentType;
    contentId: string;
    authorId: string;
    text: string;
  }): Promise<ModerationVerdict | null> {
    try {
      const v = await this.http.data('/internal/moderation/items', Verdict, {
        method: 'POST',
        body: input,
        idempotent: true,
      });
      return { decision: v.decision, redactedText: v.redactedText ?? '', categories: v.categories ?? [] };
    } catch {
      return null;
    }
  }
}
