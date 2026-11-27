import { Injectable, signal } from '@angular/core';

/** Thông báo ngắn ở đầu trang (đã lưu / lỗi); tự ẩn sau vài giây. */
@Injectable({ providedIn: 'root' })
export class NoticeService {
  /** Thời gian hiện thông báo. */
  private static readonly VISIBLE_MS = 4_000;
  readonly notice = signal<{ kind: 'success' | 'error'; text: string } | null>(null);
  private timer: ReturnType<typeof setTimeout> | undefined;

  success(text: string): void {
    this.show({ kind: 'success', text });
  }

  error(text: string): void {
    this.show({ kind: 'error', text });
  }

  private show(notice: { kind: 'success' | 'error'; text: string }): void {
    clearTimeout(this.timer);
    this.notice.set(notice);
    this.timer = setTimeout(() => this.notice.set(null), NoticeService.VISIBLE_MS);
  }
}
