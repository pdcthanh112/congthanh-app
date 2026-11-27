import { Component, OnInit, inject, signal } from "@angular/core";
import { forkJoin } from "rxjs";
import { errorMessage } from "../core/errors";
import { AttributeFormComponent } from "./attribute-form.component";
import { CatalogApiService } from "./catalog-api.service";
import { AttributeGroup, DATA_TYPE_LABELS, ProductAttributeDef } from "./catalog.models";
import { NoticeService } from "./notice.service";

@Component({
  selector: "app-attributes-page",
  standalone: true,
  imports: [AttributeFormComponent],
  template: `
    <button type="button" (click)="editing.set(null)">Thêm thuộc tính</button>
    @if (editing() !== undefined) {
      <app-attribute-form [attribute]="editing() ?? null" [groups]="groups()" (saved)="editing.set(undefined); load()" (closed)="editing.set(undefined)" />
    }
    @if (loading()) {
      <p>Đang tải...</p>
    } @else {
      <table>
        <thead>
          <tr>
            <th>Tên</th>
            <th>Mã</th>
            <th>Kiểu</th>
            <th>Nhóm</th>
            <th>Giá trị cho phép</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          @for (a of attributes(); track a.id) {
            <tr>
              <td>{{ a.name }}</td>
              <td class="mono">{{ a.code }}</td>
              <td>{{ labels[a.dataType] }}</td>
              <td>{{ groupName(a.groupId) }}</td>
              <td>{{ a.options.length || "—" }}</td>
              <td class="right">
                <button type="button" class="secondary" (click)="editing.set(a)">Sửa</button>
              </td>
            </tr>
          } @empty {
            <tr>
              <td colspan="6">Chưa có thuộc tính</td>
            </tr>
          }
        </tbody>
      </table>
    }
  `,
})
export class AttributesPageComponent implements OnInit {
  private readonly api = inject(CatalogApiService);
  private readonly notices = inject(NoticeService);
  readonly labels = DATA_TYPE_LABELS;
  readonly attributes = signal<ProductAttributeDef[]>([]);
  readonly groups = signal<AttributeGroup[]>([]);
  readonly loading = signal(true);
  /** undefined = đóng form; null = đang tạo mới. */
  readonly editing = signal<ProductAttributeDef | null | undefined>(undefined);

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    forkJoin([this.api.attributes(), this.api.groups()]).subscribe({
      next: ([attributes, groups]) => {
        this.attributes.set(attributes);
        this.groups.set(groups);
        this.loading.set(false);
      },
      error: (e: unknown) => {
        this.loading.set(false);
        this.notices.error(errorMessage(e));
      },
    });
  }

  groupName(id: number | null): string {
    if (id === null) return "Không nhóm";
    return this.groups().find((g) => g.id === id)?.name ?? "—";
  }
}
