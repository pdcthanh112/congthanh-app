import { HttpErrorResponse } from '@angular/common/http';

/**
 * Thông báo lỗi cho người dùng. product-service trả ProblemDetail ({detail}) hoặc envelope cũ ({message}); lỗi mạng
 * hay 5xx thì báo chung, không hiện chi tiết nội bộ.
 */
export function errorMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) return 'Có lỗi xảy ra, vui lòng thử lại.';
  if (error.status === 0) return 'Không kết nối được máy chủ.';
  if (error.status === 401) return 'Phiên đăng nhập đã hết, vui lòng đăng nhập lại.';
  if (error.status === 403) return 'Bạn không có quyền thực hiện thao tác này.';
  if (error.status === 409) return conflictText(error) ?? 'Dữ liệu vừa được người khác sửa. Tải lại rồi thử lại.';
  if (error.status >= 500) return 'Máy chủ đang gặp sự cố, vui lòng thử lại sau.';
  const body = error.error as { detail?: string; message?: string } | null;
  return body?.detail ?? body?.message ?? 'Yêu cầu không hợp lệ.';
}

function conflictText(error: HttpErrorResponse): string | undefined {
  const body = error.error as { detail?: string; message?: string } | null;
  return body?.detail ?? body?.message;
}
