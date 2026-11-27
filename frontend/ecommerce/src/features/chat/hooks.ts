'use client';

import { useCallback, useEffect, useRef, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { type ChatEvent, resetChat, streamChat } from '@/api/chatbotApi';
import type { ProductCard } from '@/graphql/types';
import { CART_KEY } from '@/utils/constants/queryKey';

/** Khoá sessionStorage giữ id phiên chat trong tab (đóng tab là bắt đầu phiên mới). */
const SESSION_STORAGE_KEY = 'chatbot-session-id';

export type ChatTool = { id: string; name: string; ok?: boolean };

export type ChatMessage = {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  products: ProductCard[];
  tools: ChatTool[];
  /** Mã lỗi hoặc thông báo lỗi từ server; UI tự dịch các mã đã biết. */
  error?: string;
};

const newSessionId = () => crypto.randomUUID();

function storedSessionId(): string {
  const existing = sessionStorage.getItem(SESSION_STORAGE_KEY);
  if (existing) return existing;
  const id = newSessionId();
  sessionStorage.setItem(SESSION_STORAGE_KEY, id);
  return id;
}

/** Áp một sự kiện SSE vào tin nhắn trả lời đang stream (bất biến). */
export function applyEvent(m: ChatMessage, e: ChatEvent): ChatMessage {
  switch (e.type) {
    case 'delta':
      return { ...m, text: m.text + e.data };
    case 'products': {
      const seen = new Set(m.products.map((p) => p.id));
      return { ...m, products: [...m.products, ...e.data.filter((p) => !seen.has(p.id))] };
    }
    case 'tool_started':
      return { ...m, tools: [...m.tools, { id: e.data.id, name: e.data.name }] };
    case 'tool_finished':
      return { ...m, tools: m.tools.map((t) => (t.id === e.data.id ? { ...t, ok: e.data.ok } : t)) };
    case 'error':
      return { ...m, error: e.data };
    default:
      return m;
  }
}

/** Trạng thái hội thoại của widget: gửi câu hỏi, nhận stream, cập nhật cache giỏ hàng khi trợ lý sửa giỏ. */
export function useChat() {
  const queryClient = useQueryClient();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [pending, setPending] = useState(false);
  const abortRef = useRef<AbortController | null>(null);

  // Huỷ stream đang chạy khi widget bị gỡ khỏi trang.
  useEffect(() => () => abortRef.current?.abort(), []);

  const send = useCallback(
    async (text: string) => {
      const question = text.trim();
      if (!question || pending) return;
      const replyId = crypto.randomUUID();
      setMessages((list) => [
        ...list,
        { id: crypto.randomUUID(), role: 'user', text: question, products: [], tools: [] },
        { id: replyId, role: 'assistant', text: '', products: [], tools: [] },
      ]);
      setPending(true);
      const controller = new AbortController();
      abortRef.current = controller;
      const update = (e: ChatEvent) => {
        if (e.type === 'cart_updated') queryClient.setQueryData([CART_KEY], e.data);
        setMessages((list) => list.map((m) => (m.id === replyId ? applyEvent(m, e) : m)));
      };
      try {
        await streamChat(question, storedSessionId(), update, controller.signal);
      } catch (err) {
        if (!controller.signal.aborted) update({ type: 'error', data: err instanceof Error ? err.message : 'NETWORK' });
      } finally {
        setPending(false);
      }
    },
    [pending, queryClient]
  );

  const reset = useCallback(async () => {
    abortRef.current?.abort();
    const old = sessionStorage.getItem(SESSION_STORAGE_KEY);
    sessionStorage.setItem(SESSION_STORAGE_KEY, newSessionId());
    setMessages([]);
    setPending(false);
    if (old) await resetChat(old).catch(() => undefined);
  }, []);

  return { messages, pending, send, reset };
}
