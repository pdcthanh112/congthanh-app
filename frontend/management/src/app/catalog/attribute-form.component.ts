import { Component, EventEmitter, Input, OnInit, Output, inject } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { errorMessage } from "../core/errors";
import { CatalogApiService } from "./catalog-api.service";
import { ATTRIBUTE_DATA_TYPES, AttributeGroup, DATA_TYPE_LABELS, ProductAttributeDef } from "./catalog.models";
import { NoticeService } from "./notice.service";

const CODE_PATTERN = /^[a-z][a-z0-9_]{0,63}$/;

@Component({
  selector: "app-attribute-form",
  standalone: true,
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="card grid">
      <label
        >Tên
        <input formControlName="name" maxlength="255" />
        @if (form.controls.name.touched && form.controls.name.invalid) {
          <small class="error">Bắt buộc nhập</small>
        }
      </label>
      <label
        >Mã (code)
        <input formControlName="code" maxlength="64" />
        @if (form.controls.code.invalid) {
          <small class="error">Chữ thường, số và _, bắt đầu bằng chữ</small>
        }
        @if (form.errors?.["codeNeeded"]) {
          <small class="error">Thuộc tính dùng làm bộ lọc phải có mã</small>
        }
      </label>
      <label
        >Kiểu dữ liệu
        <select formControlName="dataType">
          @for (type of dataTypes; track type) {
            <option [value]="type">{{ labels[type] }}</option>
          }
        </select>
      </label>
      <label>Đơn vị <input formControlName="unit" maxlength="16" /></label>
      <label
        >Nhóm
        <select formControlName="groupId">
          <option value="">Không nhóm</option>
          @for (g of groups; track g.id) {
            <option [value]="g.id">{{ g.name }}</option>
          }
        </select>
      </label>
      <label>Thứ tự <input type="number" formControlName="displayOrder" /></label>
      <label class="inline"><input type="checkbox" formControlName="filterable" /> Dùng làm bộ lọc</label>
      <label class="inline"><input type="checkbox" formControlName="searchable" /> Đưa vào tìm kiếm</label>
      @if (allowsOptions()) {
        <label class="wide"
          >Giá trị cho phép (mỗi dòng một giá trị, để trống = nhập tự do)
          <textarea rows="5" formControlName="optionsText"></textarea>
        </label>
      }
      <div class="wide actions">
        <button type="submit" [disabled]="saving">Lưu</button>
        <button type="button" class="secondary" (click)="closed.emit()">Đóng</button>
      </div>
    </form>
  `,
})
export class AttributeFormComponent implements OnInit {
  @Input() attribute: ProductAttributeDef | null = null;
  @Input() groups: AttributeGroup[] = [];
  @Output() readonly saved = new EventEmitter<void>();
  @Output() readonly closed = new EventEmitter<void>();

  private readonly api = inject(CatalogApiService);
  private readonly notices = inject(NoticeService);
  readonly dataTypes = ATTRIBUTE_DATA_TYPES;
  readonly labels = DATA_TYPE_LABELS;
  saving = false;

  readonly form = inject(FormBuilder).nonNullable.group(
    {
      name: ["", [Validators.required, Validators.maxLength(255)]],
      code: ["", Validators.pattern(CODE_PATTERN)],
      dataType: ["TEXT" as ProductAttributeDef["dataType"]],
      unit: [""],
      groupId: [""],
      displayOrder: [0],
      filterable: [false],
      searchable: [false],
      optionsText: [""],
    },
    {
      validators: (g) => (g.value.filterable && !String(g.value.code ?? "").trim() ? { codeNeeded: true } : null),
    },
  );

  ngOnInit(): void {
    const a = this.attribute;
    if (!a) return;
    this.form.setValue({
      name: a.name,
      code: a.code ?? "",
      dataType: a.dataType,
      unit: a.unit ?? "",
      groupId: a.groupId === null ? "" : String(a.groupId),
      displayOrder: a.displayOrder,
      filterable: a.filterable,
      searchable: a.searchable,
      optionsText: a.options.join("\n"),
    });
  }

  allowsOptions(): boolean {
    const type = this.form.controls.dataType.value;
    return type === "TEXT" || type === "MULTI_SELECT";
  }

  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) return;
    const v = this.form.getRawValue();
    const options = this.allowsOptions()
      ? v.optionsText
          .split("\n")
          .map((o) => o.trim())
          .filter(Boolean)
      : [];
    this.saving = true;
    this.api
      .saveAttribute({
        id: this.attribute?.id ?? null,
        name: v.name.trim(),
        code: v.code.trim() || null,
        dataType: v.dataType,
        unit: v.unit.trim() || null,
        filterable: v.filterable,
        searchable: v.searchable,
        displayOrder: Number(v.displayOrder) || 0,
        groupId: v.groupId ? Number(v.groupId) : null,
        options,
      })
      .subscribe({
        next: () => {
          this.saving = false;
          this.notices.success("Đã lưu thuộc tính");
          this.saved.emit();
        },
        error: (e: unknown) => {
          this.saving = false;
          this.notices.error(errorMessage(e));
        },
      });
  }
}
