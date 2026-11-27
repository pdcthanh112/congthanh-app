import type { Cart, ProductCard } from '@/graphql/types';

/** Đi qua BFF /api/gateway (gắn token từ cookie NextAuth), stream SSE từ chatbot-service. */
const CHAT_PATH = '/api/gateway/chatbot/chat';
const SESSION_PATH = '/api/gateway/chatbot/sessions';

/** Sự kiện SSE của chatbot-service. */
export type ChatEvent =
  | { type: 'delta'; data: string }
  | { type: 'products'; data: ProductCard[] }
  | { type: 'cart_updated'; data: Cart }
  | { type: 'tool_started'; data: { id: string; name: string } }
  | { type: 'tool_finished'; data: { id: string; name: string; ok: boolean } }
  | { type: 'done'; data: { sessionId: string | null } }
  | { type: 'error'; data: string };

const KNOWN_EVENTS = new Set(['delta', 'products', 'cart_updated', 'tool_started', 'tool_finished', 'done', 'error']);

/** Tách một khối SSE ("event: x\ndata: {...}") thành sự kiện; khối lạ thì bỏ qua. */
export function parseSseBlock(block: string): ChatEvent | null {
  let type = '';
  const data: string[] = [];
  for (const line of block.split('\n')) {
    if (line.startsWith('event:')) type = line.slice(6).trim();
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart());
  }
  if (!KNOWN_EVENTS.has(type) || data.length === 0) return null;
  try {
    return { type, data: JSON.parse(data.join('\n')) } as ChatEvent;
  } catch {
    return null;
  }
}

/**
 * Gửi một câu hỏi và gọi onEvent cho từng sự kiện tới khi stream kết thúc.
 * Lỗi HTTP (400 câu hỏi không hợp lệ, 429 quá nhiều yêu cầu...) được báo thành sự kiện error.
 */
export async function streamChat(
  message: string,
  sessionId: string,
  onEvent: (e: ChatEvent) => void,
  signal?: AbortSignal
): Promise<void> {
  const res = await fetch(CHAT_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'text/event-stream' },
    body: JSON.stringify({ message, sessionId }),
    signal,
  });
  if (!res.ok || !res.body) {
    onEvent({ type: 'error', data: res.status === 429 ? 'RATE_LIMITED' : `HTTP_${res.status}` });
    return;
  }
  const reader = res.body.pipeThrough(new TextDecoderStream()).getReader();
  let buffer = '';
  for (;;) {
    const { value, done } = await reader.read();
    if (done) break;
    buffer += value;
    let end = buffer.indexOf('\n\n');
    while (end !== -1) {
      const event = parseSseBlock(buffer.slice(0, end));
      if (event) onEvent(event);
      buffer = buffer.slice(end + 2);
      end = buffer.indexOf('\n\n');
    }
  }
}

/** Xoá lịch sử hội thoại phía server (nút "Cuộc trò chuyện mới"). */
export async function resetChat(sessionId: string): Promise<void> {
  await fetch(`${SESSION_PATH}/${encodeURIComponent(sessionId)}`, { method: 'DELETE' });
}
