import { Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { NoticeService } from './catalog/notice.service';
import { AuthService } from './core/auth.service';

/** Khung trang quản trị: tiêu đề, menu catalog, thông báo, người dùng đăng nhập. */
@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
})
export class AppComponent {
  readonly auth = inject(AuthService);
  readonly notices = inject(NoticeService);
  readonly nav = [
    { path: '/catalog/attributes', label: 'Thuộc tính' },
    { path: '/catalog/groups', label: 'Nhóm hiển thị' },
    { path: '/catalog/templates', label: 'Mẫu theo danh mục' },
    { path: '/catalog/specifications', label: 'Thông số tự do' },
  ];
}
