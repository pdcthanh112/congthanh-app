import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { forkJoin } from 'rxjs';
import { errorMessage } from '../core/errors';
import { CatalogApiService } from './catalog-api.service';
import { CategoryOption, ProductAttributeDef } from './catalog.models';
import { NoticeService } from './notice.service';

interface TemplateRow {
  attributeId: number;
  name: string;
  required: boolean;
}

/** Mẫu thông số theo danh mục: thuộc tính nào áp dụng, bắt buộc không, thứ tự trong form seller (ADR 0033). */
@Component({
  selector: 'app-templates-page',
  standalone: true,
  imports: [FormsModule],
  template: `
    <div class="card">
      <label>Danh mục
        <select name="category" [ngModel]="categoryId()" (ngModelChange)="selectCategory($event)">
          <option value="">Chọn danh mục</option>
          @for (c of categories(); track c.id) { <option [value]="c.id">{{ c.name }}</option> }
        </select>
      </label>
    </div>
    @if (categoryId()) {
      <ol class="list">
        @for (r of rows(); track r.attributeId; let i = $index; let last = $last) {
          <li class="card row">
            <span class="grow">{{ r.name }}</span>
            <label class="inline">
              <input type="checkbox" [name]="'required-' + r.attributeId" [(ngModel)]="r.required" /> Bắt buộc
            </label>
            <button type="button" class="secondary" [disabled]="i === 0" (click)="move(i, -1)">Lên</button>
            <button type="button" class="secondary" [disabled]="last" (click)="move(i, 1)">Xuống</button>
            <button type="button" class="danger" (click)="removeRow(r.attributeId)">Bỏ</button>
          </li>
        } @empty {
          <li>Danh mục chưa có mẫu</li>
        }
      </ol>
      <div class="card row">
        <label>Thêm thuộc tính vào mẫu
          <select name="adding" [(ngModel)]="adding">
            <option value="">—</option>
            @for (a of available(); track a.id) { <option [value]="a.id">{{ a.name }}</option> }
          </select>
        </label>
        <button type="button" class="secondary" [disabled]="!adding" (click)="addRow()">Thêm</button>
        <button type="button" (click)="save()">Lưu mẫu</button>
      </div>
    }
  `,
})
export class TemplatesPageComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly notices = inject(NoticeService);
  readonly categories = signal<CategoryOption[]>([]);
  readonly attributes = signal<ProductAttributeDef[]>([]);
  readonly categoryId = signal('');
  readonly rows = signal<TemplateRow[]>([]);
  readonly available = computed(() =>
    this.attributes().filter((a) => a.id !== null && !this.rows().some((r) => r.attributeId === a.id))
  );
  adding = '';

  ngOnInit(): void {
    forkJoin([this.api.categories(), this.api.attributes()]).subscribe({
      next: ([categories, attributes]) => {
        this.categories.set(categories);
        this.attributes.set(attributes);
      },
      error: (e: unknown) => this.notices.error(errorMessage(e)),
    });
  }

  selectCategory(id: string): void {
    this.categoryId.set(id);
    this.rows.set([]);
    if (!id) return;
    this.api.template(id).subscribe({
      next: (rows) => this.rows.set(rows.map((r) => ({ attributeId: r.attributeId, name: r.name, required: r.required }))),
      error: (e: unknown) => this.notices.error(errorMessage(e)),
    });
  }

  move(index: number, delta: number): void {
    const list = [...this.rows()];
    const target = index + delta;
    if (target < 0 || target >= list.length) return;
    [list[index], list[target]] = [list[target], list[index]];
    this.rows.set(list);
  }

  addRow(): void {
    const attribute = this.attributes().find((a) => String(a.id) === this.adding);
    if (!attribute || attribute.id === null) return;
    this.rows.set([...this.rows(), { attributeId: attribute.id, name: attribute.name, required: false }]);
    this.adding = '';
  }

  removeRow(attributeId: number): void {
    this.rows.set(this.rows().filter((r) => r.attributeId !== attributeId));
  }

  save(): void {
    const rows = this.rows().map((r) => ({ attributeId: r.attributeId, required: r.required }));
    this.api.saveTemplate(this.categoryId(), rows).subscribe({
      next: () => this.notices.success('Đã lưu mẫu'),
      error: (e: unknown) => this.notices.error(errorMessage(e)),
    });
  }
}
