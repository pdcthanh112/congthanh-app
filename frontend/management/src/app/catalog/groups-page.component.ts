import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { errorMessage } from '../core/errors';
import { CatalogApiService } from './catalog-api.service';
import { AttributeGroup } from './catalog.models';
import { NoticeService } from './notice.service';

/** Bước thứ tự mặc định khi thêm nhóm mới, chừa chỗ chèn nhóm vào giữa. */
const ORDER_STEP = 10;

/** Nhóm hiển thị của bảng thông số ("Màn hình", "Pin"): tạo, đổi tên/thứ tự, xoá. */
@Component({
  selector: 'app-groups-page',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="card row" (ngSubmit)="create()">
      <label>Tên nhóm mới <input name="newName" [(ngModel)]="newName" maxlength="255" /></label>
      <button type="submit" [disabled]="!newName.trim()">Thêm nhóm</button>
    </form>
    <ul class="list">
      @for (g of drafts(); track g.id) {
        <li class="card row">
          <label>Tên <input [name]="'name-' + g.id" [(ngModel)]="g.name" maxlength="255" /></label>
          <label>Thứ tự <input type="number" [name]="'order-' + g.id" [(ngModel)]="g.displayOrder" /></label>
          <button type="button" [disabled]="!g.name.trim()" (click)="save(g)">Lưu</button>
          <button type="button" class="danger" (click)="remove(g)">Xoá</button>
        </li>
      } @empty {
        <li>Chưa có nhóm</li>
      }
    </ul>
  `,
})
export class GroupsPageComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly notices = inject(NoticeService);
  /** Bản sao để sửa tại chỗ; tải lại từ server sau mỗi lần lưu. */
  readonly drafts = signal<AttributeGroup[]>([]);
  newName = '';

  ngOnInit(): void {
    this.load();
  }

  create(): void {
    const name = this.newName.trim();
    if (!name) return;
    this.api.saveGroup(null, name, this.drafts().length * ORDER_STEP).subscribe(this.done('Đã thêm nhóm', () => (this.newName = '')));
  }

  save(g: AttributeGroup): void {
    this.api.saveGroup(g.id, g.name.trim(), Number(g.displayOrder) || 0).subscribe(this.done('Đã lưu nhóm'));
  }

  remove(g: AttributeGroup): void {
    if (!window.confirm(`Xoá nhóm "${g.name}"? Các thuộc tính trong nhóm sẽ thành không nhóm.`)) return;
    this.api.deleteGroup(g.id).subscribe(this.done('Đã xoá nhóm'));
  }

  private load(): void {
    this.api.groups().subscribe({
      next: (groups) => this.drafts.set(groups.map((g) => ({ ...g }))),
      error: (e: unknown) => this.notices.error(errorMessage(e)),
    });
  }

  private done(message: string, after?: () => void) {
    return {
      next: () => {
        this.notices.success(message);
        after?.();
        this.load();
      },
      error: (e: unknown) => this.notices.error(errorMessage(e)),
    };
  }
}
