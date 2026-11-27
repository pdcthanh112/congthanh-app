import { HttpErrorResponse } from '@angular/common/http';
import { errorMessage } from './errors';

describe('errorMessage', () => {
  it('lấy detail của ProblemDetail cho lỗi 400', () => {
    const e = new HttpErrorResponse({ status: 400, error: { detail: 'Chất liệu: accepts a single value' } });
    expect(errorMessage(e)).toBe('Chất liệu: accepts a single value');
  });

  it('không lộ chi tiết lỗi 5xx', () => {
    const e = new HttpErrorResponse({ status: 500, error: { detail: 'SQL error at products' } });
    expect(errorMessage(e)).toBe('Máy chủ đang gặp sự cố, vui lòng thử lại sau.');
  });
});
