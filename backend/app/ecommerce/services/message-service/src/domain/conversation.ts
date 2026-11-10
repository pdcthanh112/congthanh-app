import { DomainError } from '@ecom/platform/errors';

export type ConversationKind = 'SELLER' | 'SUPPORT';
export const SUPPORT_INBOX = 'support';
export const MAX_BODY = 4000;
export const MAX_ATTACHMENTS = 5;

export interface Conversation {
  id: string;
  kind: ConversationKind;
  customerId: string;
  /** Id seller (kind SELLER) hoặc "support" (kind SUPPORT: mọi nhân viên SUPPORT/ADMIN xem được). */
  counterpartId: string;
  productId: string | null;
  orderId: string | null;
  lastSeq: number;
  lastMessageAt: Date | null;
  lastMessagePreview: string | null;
  createdAt: Date;
}

export interface ChatMessage {
  id: string;
  conversationId: string;
  seq: number;
  senderId: string;
  body: string;
  attachments: string[];
  clientMessageId: string | null;
  createdAt: Date;
}

export interface Viewer {
  id: string;
  isSupport: boolean;
}

/** Ai được đọc/gửi trong cuộc trò chuyện: khách, seller đối diện, hoặc nhân viên hỗ trợ với hội thoại SUPPORT. */
export function canAccess(c: Conversation, v: Viewer): boolean {
  if (c.customerId === v.id) return true;
  if (c.kind === 'SELLER') return c.counterpartId === v.id;
  return v.isSupport;
}

export function assertAccess(c: Conversation | undefined, v: Viewer): Conversation {
  if (!c || !canAccess(c, v)) throw DomainError.notFound('conversation not found');
  return c;
}

/** Người nhận (để đẩy realtime và thông báo): mọi bên trừ người gửi; SUPPORT không có id cụ thể. */
export function recipientsOf(c: Conversation, senderId: string): string[] {
  return [c.customerId, c.counterpartId].filter((id) => id !== senderId && id !== SUPPORT_INBOX);
}

// Bỏ ký tự điều khiển (trừ xuống dòng/tab) để tránh chèn chuỗi lạ vào UI/log.
// biome-ignore lint/suspicious/noControlCharactersInRegex: cố ý khớp ký tự điều khiển để loại bỏ
const CONTROL = /[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g;

export function normalizeBody(body: string, attachments: readonly string[], mediaPrefix: string) {
  const text = (body ?? '').replace(CONTROL, '').trim();
  const files = [...new Set(attachments)];
  if (!text && files.length === 0) throw DomainError.invalid('message is empty');
  if (text.length > MAX_BODY) throw DomainError.invalid(`message is at most ${MAX_BODY} characters`);
  if (files.length > MAX_ATTACHMENTS) throw DomainError.invalid(`at most ${MAX_ATTACHMENTS} attachments`);
  const bad = files.find((f) => !f.startsWith(mediaPrefix));
  if (bad) throw DomainError.invalid('attachments must be uploaded via file-service', 'INVALID_MEDIA');
  return { body: text, attachments: files };
}

export const previewOf = (m: Pick<ChatMessage, 'body' | 'attachments'>) =>
  m.body ? m.body.slice(0, 100) : `[${m.attachments.length} tệp đính kèm]`;
