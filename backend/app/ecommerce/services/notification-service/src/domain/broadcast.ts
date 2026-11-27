import { DomainError } from '@ecommerce/platform/errors';
import { type Category, CHANNELS, type Channel, TRANSACTIONAL } from './notification.js';

/**
 * Chiến dịch gửi hàng loạt (ADR 0034): admin gửi email/thông báo tới mọi người dùng hoặc một nhóm role,
 * gửi ngay hoặc hẹn giờ (ví dụ trước flash sale).
 *
 * Vòng đời: SCHEDULED → EXPANDING (đang chia trang người nhận) → SENDING (đã xếp hàng hết, worker đang gửi)
 * → COMPLETED. Huỷ được khi chưa COMPLETED.
 */
export type BroadcastStatus = 'SCHEDULED' | 'EXPANDING' | 'SENDING' | 'COMPLETED' | 'CANCELLED';

export type BroadcastAudience = { kind: 'ALL_USERS' } | { kind: 'ROLE'; role: string };

/** Nhóm được gửi hàng loạt; nhóm giao dịch (đơn hàng, thanh toán) không bao giờ đi qua broadcast. */
export const BROADCAST_CATEGORIES: readonly Category[] = ['PROMOTION', 'SYSTEM'];

export const MAX_TITLE_LENGTH = 200;
export const MAX_BODY_LENGTH = 5_000;

export interface Broadcast {
  id: string;
  category: Category;
  title: string;
  body: string;
  link: string | null;
  channels: Channel[];
  audience: BroadcastAudience;
  status: BroadcastStatus;
  scheduledAt: Date;
  /** Vị trí đã chia tới trong danh sách người nhận: chết giữa chừng thì chạy tiếp từ đây. */
  audienceCursor: number;
  /** Số thông báo đã tạo (đã trừ người tắt nhóm này và người nhận trùng). */
  recipients: number;
  createdBy: string;
  createdAt: Date;
  completedAt: Date | null;
  cancelledAt: Date | null;
}

export interface BroadcastDraft {
  category: Category;
  title: string;
  body: string;
  link: string | null;
  channels: readonly Channel[];
  audience: BroadcastAudience;
  /** Bỏ trống = gửi ngay. */
  scheduledAt: Date | null;
}

/** Kiểm tra bản nháp và tạo chiến dịch ở trạng thái SCHEDULED. Ném DomainError.invalid khi dữ liệu sai. */
export function scheduleBroadcast(id: string, draft: BroadcastDraft, createdBy: string, now: Date): Broadcast {
  const title = draft.title.trim();
  const body = draft.body.trim();
  if (!BROADCAST_CATEGORIES.includes(draft.category)) {
    throw DomainError.invalid(`category ${draft.category} cannot be broadcast`);
  }
  if (TRANSACTIONAL.has(draft.category)) throw DomainError.invalid('transactional notifications cannot be broadcast');
  if (!title) throw DomainError.invalid('title is required');
  if (title.length > MAX_TITLE_LENGTH)
    throw DomainError.invalid(`title must be at most ${MAX_TITLE_LENGTH} characters`);
  if (!body) throw DomainError.invalid('body is required');
  if (body.length > MAX_BODY_LENGTH) throw DomainError.invalid(`body must be at most ${MAX_BODY_LENGTH} characters`);
  if (draft.link && !draft.link.startsWith('/'))
    throw DomainError.invalid('link must be an in-app path starting with /');
  const channels = [...new Set(draft.channels)];
  if (channels.length === 0) throw DomainError.invalid('at least one channel is required');
  for (const c of channels) {
    if (!CHANNELS.includes(c)) throw DomainError.invalid(`unknown channel ${c}`);
  }
  if (draft.audience.kind === 'ROLE' && !draft.audience.role.trim()) throw DomainError.invalid('role is required');
  if (draft.scheduledAt && Number.isNaN(draft.scheduledAt.getTime())) throw DomainError.invalid('invalid scheduledAt');

  return {
    id,
    category: draft.category,
    title,
    body,
    link: draft.link || null,
    channels,
    audience: draft.audience.kind === 'ROLE' ? { kind: 'ROLE', role: draft.audience.role.trim() } : draft.audience,
    status: 'SCHEDULED',
    // Hẹn giờ trong quá khứ nghĩa là gửi ngay.
    scheduledAt: draft.scheduledAt && draft.scheduledAt > now ? draft.scheduledAt : now,
    audienceCursor: 0,
    recipients: 0,
    createdBy,
    createdAt: now,
    completedAt: null,
    cancelledAt: null,
  };
}

/** Huỷ được khi chiến dịch chưa xong; đã COMPLETED/CANCELLED thì ném DomainError.conflict. */
export function requireCancellable(b: Broadcast): void {
  if (b.status === 'COMPLETED') throw DomainError.conflict(`broadcast ${b.id} is already completed`);
  if (b.status === 'CANCELLED') throw DomainError.conflict(`broadcast ${b.id} is already cancelled`);
}

/** Khoá chống trùng: mỗi người nhận một thông báo cho mỗi chiến dịch, kể cả khi trang người nhận bị chia lại. */
export const broadcastDedupKey = (broadcastId: string, userId: string) => `bc:${broadcastId}:${userId}`;
