import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { errorMessage } from '../core/errors';
import { CatalogApiService } from './catalog-api.service';
import { AdminProduct } from './catalog.models';
import { NoticeService } from './notice.service';
import { DraftGroup, fromDraft, newKey, toDraft } from './specifications';

/** Thông số tự do của một sản phẩm gốc, chỉ để hiển thị (ADR 0033). Mở theo id, hoặc ?productId= trên URL. */
@Component({
  selector: 'app-specifications-page',
  standalone: true,
  imports: [FormsModule],
  template: `
    <form class="card row" (ngSubmit)="open()">
      <label>Mã sản phẩm <input name="productId" [(ngModel)]="productId" /></label>
      <button type="submit" class="secondary" [disabled]="!productId.trim()">Mở</button>
    </form>
    @if (product(); as p) {
      <h2>{{ p.name }}</h2>
      <p class="hint">Chỉ để hiển thị. Thông số cần lọc hoặc so sánh hãy khai báo là thuộc tính.</p>
      @for (g of groups; track g.key; let gi = $index) {
        <fieldset class="card">
          <legend>Nhóm {{ gi + 1 }}</legend>
          <div class="row">
            <label class="grow">Tên nhóm (để trống nếu không cần tiêu đề)
              <input [name]="'group-' + g.key" [(ngModel)]="g.name" maxlength="120" />
            </label>
            <button type="button" class="danger" (click)="removeGroup(g.key)">Bỏ nhóm</button>
          </div>
          @for (item of g.items; track item.key) {
            <div class="row">
              <label>Tên thông số <input [name]="'name-' + item.key" [(ngModel)]="item.name" maxlength="120" /></label>
              <label class="grow">Giá trị <input [name]="'value-' + item.key" [(ngModel)]="item.value" maxlength="1000" /></label>
              <button type="button" class="secondary" (click)="removeItem(g, item.key)">Bỏ</button>
            </div>
          }
          <button type="button" class="secondary" (click)="addItem(g)">Thêm dòng</button>
        </fieldset>
      }
      <div class="row">
        <button type="button" class="secondary" (click)="addGroup()">Thêm nhóm</button>
        <button type="button" [disabled]="saving" (click)="save(p)">Lưu</button>
      </div>
    }
  `,
})
export class SpecificationsPageComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly notices = inject(NoticeService);
  private readonly route = inject(ActivatedRoute);
  readonly product = signal<AdminProduct | null>(null);
  groups: DraftGroup[] = [];
  productId = '';
  saving = false;

  ngOnInit(): void {
    const fromUrl = this.route.snapshot.queryParamMap.get('productId');
    if (fromUrl) {
      this.productId = fromUrl;
      this.open();
    }
  }

  open(): void {
    const id = this.productId.trim();
    if (!id) return;
    this.api.product(id).subscribe({
      next: (p) => this.show(p),
      error: (e: unknown) => {
        this.product.set(null);
        this.notices.error(errorMessage(e));
      },
    });
  }

  addGroup(): void {
    this.groups = [...this.groups, { key: newKey(), name: '', items: [{ key: newKey(), name: '', value: '' }] }];
  }

  removeGroup(key: number): void {
    this.groups = this.groups.filter((g) => g.key !== key);
  }

  addItem(group: DraftGroup): void {
    group.items = [...group.items, { key: newKey(), name: '', value: '' }];
  }

  removeItem(group: DraftGroup, key: number): void {
    group.items = group.items.filter((i) => i.key !== key);
  }

  save(p: AdminProduct): void {
    this.saving = true;
    this.api.saveSpecifications(p.id, p.version, fromDraft(this.groups)).subscribe({
      next: (saved) => {
        this.saving = false;
        this.notices.success('Đã lưu thông số');
        this.show(saved);
      },
      error: (e: unknown) => {
        this.saving = false;
        this.notices.error(errorMessage(e));
      },
    });
  }

  private show(p: AdminProduct): void {
    this.product.set(p);
    this.groups = toDraft(p.specifications);
  }
}
