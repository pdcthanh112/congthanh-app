import { describe, expect, it } from 'vitest';
import { type Conversation, canAccess, normalizeBody, previewOf, recipientsOf } from './conversation.js';

const P = 'https://cdn/x/';
const seller: Conversation = {
  id: 'c',
  kind: 'SELLER',
  customerId: 'cus',
  counterpartId: 'sel',
  productId: 'p',
  orderId: null,
  lastSeq: 0,
  lastMessageAt: null,
  lastMessagePreview: null,
  createdAt: new Date(),
};
const support: Conversation = { ...seller, kind: 'SUPPORT', counterpartId: 'support', productId: null };

describe('quyền truy cập', () => {
  it('khách và đúng seller; nhân viên hỗ trợ chỉ với hội thoại SUPPORT', () => {
    expect(canAccess(seller, { id: 'cus', isSupport: false })).toBe(true);
    expect(canAccess(seller, { id: 'sel', isSupport: false })).toBe(true);
    expect(canAccess(seller, { id: 'other', isSupport: true })).toBe(false);
    expect(canAccess(support, { id: 'agent', isSupport: true })).toBe(true);
    expect(canAccess(support, { id: 'other', isSupport: false })).toBe(false);
  });
  it('người nhận không gồm người gửi và hộp thư hỗ trợ', () => {
    expect(recipientsOf(seller, 'cus')).toEqual(['sel']);
    expect(recipientsOf(support, 'cus')).toEqual([]);
    expect(recipientsOf(support, 'agent')).toEqual(['cus']);
  });
});

describe('nội dung', () => {
  it('bỏ ký tự điều khiển, chặn rỗng/dài/link ngoài', () => {
    expect(normalizeBody(' xin\u0007 chào\n ', [], P).body).toBe('xin chào');
    expect(() => normalizeBody('  ', [], P)).toThrow(/empty/);
    expect(() => normalizeBody('a'.repeat(4001), [], P)).toThrow(/4000/);
    expect(() => normalizeBody('', ['https://evil/a.png'], P)).toThrow(/file-service/);
    expect(previewOf({ body: '', attachments: ['a', 'b'] })).toBe('[2 tệp đính kèm]');
  });
});
