import { describe, expect, it } from 'vitest';
import { type BroadcastDraft, broadcastDedupKey, requireCancellable, scheduleBroadcast } from './broadcast.js';

const now = new Date('2026-12-01T00:00:00Z');
const draft: BroadcastDraft = {
  category: 'PROMOTION',
  title: '  Flash sale 12.12  ',
  body: 'Giảm tới 50%',
  link: '/flash-sale',
  channels: ['IN_APP', 'EMAIL', 'EMAIL'],
  audience: { kind: 'ALL_USERS' },
  scheduledAt: null,
};

describe('tạo chiến dịch gửi hàng loạt', () => {
  it('gửi ngay khi không hẹn giờ, bỏ khoảng trắng và kênh trùng', () => {
    const b = scheduleBroadcast('1', draft, 'admin', now);
    expect(b).toMatchObject({
      status: 'SCHEDULED',
      title: 'Flash sale 12.12',
      channels: ['IN_APP', 'EMAIL'],
      scheduledAt: now,
      audienceCursor: 0,
    });
  });

  it('giữ giờ hẹn trong tương lai, hẹn trong quá khứ thì gửi ngay', () => {
    const later = new Date(now.getTime() + 3_600_000);
    expect(scheduleBroadcast('1', { ...draft, scheduledAt: later }, 'admin', now).scheduledAt).toEqual(later);
    const earlier = new Date(now.getTime() - 60_000);
    expect(scheduleBroadcast('1', { ...draft, scheduledAt: earlier }, 'admin', now).scheduledAt).toEqual(now);
  });

  it('từ chối nhóm giao dịch', () => {
    expect(() => scheduleBroadcast('1', { ...draft, category: 'ORDER' }, 'admin', now)).toThrow('cannot be broadcast');
  });

  it('từ chối tiêu đề rỗng, không có kênh, link ngoài app, role rỗng', () => {
    expect(() => scheduleBroadcast('1', { ...draft, title: '  ' }, 'admin', now)).toThrow('title is required');
    expect(() => scheduleBroadcast('1', { ...draft, channels: [] }, 'admin', now)).toThrow('at least one channel');
    expect(() => scheduleBroadcast('1', { ...draft, link: 'https://evil.example' }, 'admin', now)).toThrow(
      'in-app path',
    );
    expect(() => scheduleBroadcast('1', { ...draft, audience: { kind: 'ROLE', role: ' ' } }, 'admin', now)).toThrow(
      'role is required',
    );
  });

  it('chỉ huỷ được chiến dịch chưa xong', () => {
    const b = scheduleBroadcast('1', draft, 'admin', now);
    expect(() => requireCancellable({ ...b, status: 'SENDING' })).not.toThrow();
    expect(() => requireCancellable({ ...b, status: 'COMPLETED' })).toThrow('already completed');
    expect(() => requireCancellable({ ...b, status: 'CANCELLED' })).toThrow('already cancelled');
  });

  it('khoá chống trùng theo chiến dịch và người nhận', () => {
    expect(broadcastDedupKey('9', 'u1')).toBe('bc:9:u1');
  });
});
