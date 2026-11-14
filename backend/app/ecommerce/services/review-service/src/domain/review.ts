import { DomainError } from '@ecommerce/platform/errors';

export type ReviewStatus = 'PUBLISHED' | 'HIDDEN';
export const EDIT_WINDOW_DAYS = 30;
export const MAX_MEDIA = 6;

export interface ReviewProps {
  id: string;
  productId: string;
  customerId: string;
  authorName: string;
  orderId: string;
  rating: number;
  title: string | null;
  content: string | null;
  mediaUrls: string[];
  status: ReviewStatus;
  moderationReason: string | null;
  helpfulCount: number;
  sellerReply: string | null;
  repliedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  version: number;
}

export interface ReviewDraft {
  rating: number;
  title?: string | null | undefined;
  content?: string | null | undefined;
  mediaUrls?: readonly string[] | undefined;
}

/** Đánh giá của một khách cho một sản phẩm trong một đơn đã mua. */
export class Review {
  private constructor(private props: ReviewProps) {}

  static submit(
    base: Pick<ReviewProps, 'id' | 'productId' | 'customerId' | 'authorName' | 'orderId'>,
    draft: ReviewDraft,
    allowedMediaPrefix: string,
    now: Date,
  ): Review {
    return new Review({
      ...base,
      ...validate(draft, allowedMediaPrefix),
      status: 'PUBLISHED',
      moderationReason: null,
      helpfulCount: 0,
      sellerReply: null,
      repliedAt: null,
      createdAt: now,
      updatedAt: now,
      version: 0,
    });
  }

  static rehydrate(props: ReviewProps): Review {
    return new Review({ ...props, mediaUrls: [...props.mediaUrls] });
  }

  /** Chỉ chính tác giả, trong 30 ngày, và khi chưa bị ẩn. */
  edit(customerId: string, draft: ReviewDraft, allowedMediaPrefix: string, now: Date): void {
    this.assertAuthor(customerId);
    if (this.props.status === 'HIDDEN') throw DomainError.conflict('hidden reviews cannot be edited', 'REVIEW_HIDDEN');
    const ageDays = (now.getTime() - this.props.createdAt.getTime()) / 86_400_000;
    if (ageDays > EDIT_WINDOW_DAYS) {
      throw DomainError.conflict(`reviews can only be edited within ${EDIT_WINDOW_DAYS} days`, 'EDIT_WINDOW_CLOSED');
    }
    this.props = { ...this.props, ...validate(draft, allowedMediaPrefix), updatedAt: now };
  }

  reply(text: string, now: Date): void {
    const reply = text?.trim();
    if (!reply) throw DomainError.invalid('reply is required');
    if (reply.length > 2000) throw DomainError.invalid('reply is at most 2000 characters');
    if (this.props.sellerReply) throw DomainError.conflict('review already has a reply', 'ALREADY_REPLIED');
    this.props = { ...this.props, sellerReply: reply, repliedAt: now, updatedAt: now };
  }

  moderate(status: ReviewStatus, reason: string | null, now: Date): void {
    if (status === 'HIDDEN' && !reason?.trim()) throw DomainError.invalid('a reason is required to hide a review');
    this.props = {
      ...this.props,
      status,
      moderationReason: status === 'HIDDEN' ? reason!.trim() : null,
      updatedAt: now,
    };
  }

  /**
   * Áp quyết định kiểm duyệt (moderation-service). REDACT thay nội dung bằng bản đã che (dòng đầu là tiêu đề nếu có);
   * REJECT/ESCALATE ẩn đánh giá (ESCALATE: chờ người duyệt). Không bao giờ ẩn vì đánh giá tiêu cực: moderation-service
   * không xét số sao.
   */
  applyModeration(decision: 'APPROVE' | 'REJECT' | 'REDACT' | 'ESCALATE', redactedText: string, now: Date): void {
    switch (decision) {
      case 'APPROVE':
        this.props = { ...this.props, status: 'PUBLISHED', moderationReason: null, updatedAt: now };
        return;
      case 'REDACT': {
        let title = this.props.title;
        let content = redactedText.trim() || null;
        if (title && content?.startsWith(`${title}\n`)) {
          content = content.slice(title.length + 1).trim() || null;
        } else if (title && content) {
          const nl = content.indexOf('\n');
          if (nl > 0) {
            title = content.slice(0, nl).trim() || title;
            content = content.slice(nl + 1).trim() || null;
          }
        }
        this.props = { ...this.props, title, content, status: 'PUBLISHED', moderationReason: null, updatedAt: now };
        return;
      }
      case 'REJECT':
        this.props = {
          ...this.props,
          status: 'HIDDEN',
          moderationReason: 'removed by content moderation',
          updatedAt: now,
        };
        return;
      case 'ESCALATE':
        this.props = { ...this.props, status: 'HIDDEN', moderationReason: 'pending moderation review', updatedAt: now };
        return;
    }
  }

  /** Văn bản gửi kiểm duyệt: tiêu đề + nội dung. */
  moderationText(): string {
    return [this.props.title, this.props.content].filter(Boolean).join('\n');
  }

  get customerId() {
    return this.props.customerId;
  }

  assertAuthor(customerId: string): void {
    if (this.props.customerId !== customerId) throw DomainError.forbidden('not the author of this review');
  }

  get id() {
    return this.props.id;
  }
  get productId() {
    return this.props.productId;
  }
  get rating() {
    return this.props.rating;
  }
  get status() {
    return this.props.status;
  }
  snapshot(): Readonly<ReviewProps> {
    return { ...this.props, mediaUrls: [...this.props.mediaUrls] };
  }
}

function validate(d: ReviewDraft, mediaPrefix: string) {
  if (!Number.isInteger(d.rating) || d.rating < 1 || d.rating > 5) {
    throw DomainError.invalid('rating must be an integer from 1 to 5', 'INVALID_RATING');
  }
  const title = d.title?.trim() || null;
  const content = d.content?.trim() || null;
  if (title && title.length > 150) throw DomainError.invalid('title is at most 150 characters');
  if (content && content.length > 5000) throw DomainError.invalid('content is at most 5000 characters');
  const mediaUrls = [...new Set(d.mediaUrls ?? [])];
  if (mediaUrls.length > MAX_MEDIA) throw DomainError.invalid(`at most ${MAX_MEDIA} photos/videos`);
  // Chỉ nhận file đã tải lên qua file-service (tránh nhúng link ngoài/độc hại).
  const bad = mediaUrls.find((u) => !u.startsWith(mediaPrefix));
  if (bad) throw DomainError.invalid(`media must be uploaded via file-service: ${bad}`, 'INVALID_MEDIA');
  return { rating: d.rating, title, content, mediaUrls };
}

/** "Alex Johnson" → "A***n": người khác chỉ thấy tên đã che. */
export function maskName(name: string): string {
  const n = name.trim();
  if (n.length <= 2) return `${n[0] ?? '*'}*`;
  return `${n[0]}***${n[n.length - 1]}`;
}
