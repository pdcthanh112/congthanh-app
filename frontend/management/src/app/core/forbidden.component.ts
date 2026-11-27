import { Component, inject } from '@angular/core';
import { AuthService } from './auth.service';

/** Tài khoản đăng nhập được nhưng không có role ADMIN. */
@Component({
  selector: 'app-forbidden',
  standalone: true,
  template: `
    <div class="card">
      <h2>Không có quyền</h2>
      <p>Tài khoản {{ auth.username() }} không có quyền quản trị (cần role ADMIN).</p>
      <button type="button" (click)="auth.logout()">Đăng xuất</button>
    </div>
  `,
})
export class ForbiddenComponent {
  readonly auth = inject(AuthService);
}
