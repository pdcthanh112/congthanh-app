import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { parseSseBlock } from '@/api/chatbotApi';
import { applyEvent, type ChatMessage } from '../hooks';
import MessageText from '../MessageText';

vi.mock('@/i18n/routing', () => ({
  Link: ({ href, children }: { href: string; children: React.ReactNode }) => <a href={href}>{children}</a>,
}));

const reply: ChatMessage = { id: 'r1', role: 'assistant', text: '', products: [], tools: [] };
const card = (id: string) =>
  ({ id, name: id, slug: id, price: { amount: 1, currency: 'USD', formatted: '$1.00' } }) as never;

describe('chat SSE', () => {
  it('tách khối SSE thành sự kiện', () => {
    expect(parseSseBlock('event: delta\ndata: "Xin chào"')).toEqual({ type: 'delta', data: 'Xin chào' });
  });

  it('bỏ qua sự kiện lạ hoặc JSON hỏng', () => {
    expect(parseSseBlock('event: hack\ndata: {}')).toBeNull();
    expect(parseSseBlock('event: delta\ndata: {oops')).toBeNull();
  });
});

describe('applyEvent', () => {
  it('nối chữ, gom thẻ sản phẩm không trùng, theo dõi tool', () => {
    let m = applyEvent(reply, { type: 'delta', data: 'Có ' });
    m = applyEvent(m, { type: 'delta', data: 'áo' });
    m = applyEvent(m, { type: 'products', data: [card('p1')] });
    m = applyEvent(m, { type: 'products', data: [card('p1'), card('p2')] });
    m = applyEvent(m, { type: 'tool_started', data: { id: 't1', name: 'search_products' } });
    m = applyEvent(m, { type: 'tool_finished', data: { id: 't1', name: 'search_products', ok: true } });
    expect(m.text).toBe('Có áo');
    expect(m.products.map((p) => p.id)).toEqual(['p1', 'p2']);
    expect(m.tools).toEqual([{ id: 't1', name: 'search_products', ok: true }]);
  });

  it('không sửa tin nhắn gốc', () => {
    applyEvent(reply, { type: 'delta', data: 'x' });
    expect(reply.text).toBe('');
  });
});

describe('MessageText', () => {
  it('chỉ biến link sản phẩm nội bộ thành link, giữ link ngoài là chữ', () => {
    render(<MessageText text="Xem [Áo thun](/product/ao-thun) hoặc https://evil.example nhé" />);
    expect(screen.getByRole('link', { name: 'Áo thun' })).toHaveAttribute('href', '/product/ao-thun');
    expect(screen.queryByRole('link', { name: /evil/ })).toBeNull();
  });
});
