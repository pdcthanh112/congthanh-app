import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { APP_CONFIG } from '../core/config';
import {
  AdminProduct,
  AttributeGroup,
  CategoryOption,
  CategoryTemplateRow,
  ProductAttributeDef,
  SpecificationGroup,
} from './catalog.models';

/** Envelope cũ của product-service: {status, message, data}. */
interface Envelope<T> {
  data: T;
}

interface CategoriesResult {
  data: { categories: { id: string; name: string; children: { id: string; name: string }[] }[] };
}

const CATEGORIES_QUERY = 'query { categories(parentId: null) { id name children { id name } } }';

/** REST product-service (ghi cần ADMIN, product-service tự kiểm) và GraphQL storefront để đọc danh mục. */
@Injectable({ providedIn: 'root' })
export class CatalogApiService {
  private readonly http = inject(HttpClient);
  private readonly base = `${APP_CONFIG.apiBase}/product-attribute`;

  // ---------- thuộc tính ----------

  attributes(): Observable<ProductAttributeDef[]> {
    return this.http
      .get<Envelope<ProductAttributeDef[]>>(this.base)
      .pipe(map((r) => r.data.map((a) => ({ ...a, options: a.options ?? [] }))));
  }

  saveAttribute(attribute: ProductAttributeDef): Observable<ProductAttributeDef> {
    const request =
      attribute.id === null
        ? this.http.post<Envelope<ProductAttributeDef>>(this.base, attribute)
        : this.http.put<Envelope<ProductAttributeDef>>(`${this.base}/${attribute.id}`, attribute);
    return request.pipe(map((r) => r.data));
  }

  // ---------- nhóm ----------

  groups(): Observable<AttributeGroup[]> {
    return this.http.get<Envelope<AttributeGroup[]>>(`${this.base}/groups`).pipe(map((r) => r.data));
  }

  saveGroup(id: number | null, name: string, displayOrder: number): Observable<AttributeGroup> {
    const body = { name, displayOrder };
    const request =
      id === null
        ? this.http.post<Envelope<AttributeGroup>>(`${this.base}/groups`, body)
        : this.http.put<Envelope<AttributeGroup>>(`${this.base}/groups/${id}`, body);
    return request.pipe(map((r) => r.data));
  }

  deleteGroup(id: number): Observable<void> {
    return this.http.delete<void>(`${this.base}/groups/${id}`);
  }

  // ---------- mẫu theo danh mục ----------

  /** Danh mục gốc và một cấp con, phẳng hoá để chọn. */
  categories(): Observable<CategoryOption[]> {
    return this.http.post<CategoriesResult>(APP_CONFIG.graphqlUrl, { query: CATEGORIES_QUERY }).pipe(
      map((r) =>
        r.data.categories.flatMap((c) => [
          { id: c.id, name: c.name },
          ...c.children.map((child) => ({ id: child.id, name: `${c.name} › ${child.name}` })),
        ])
      )
    );
  }

  template(categoryId: string): Observable<CategoryTemplateRow[]> {
    return this.http
      .get<Envelope<CategoryTemplateRow[]>>(`${this.base}/categories/${encodeURIComponent(categoryId)}`)
      .pipe(map((r) => r.data));
  }

  saveTemplate(categoryId: string, rows: { attributeId: number; required: boolean }[]): Observable<CategoryTemplateRow[]> {
    const attributes = rows.map((r, i) => ({ ...r, displayOrder: i }));
    return this.http
      .put<Envelope<CategoryTemplateRow[]>>(`${this.base}/categories/${encodeURIComponent(categoryId)}`, { attributes })
      .pipe(map((r) => r.data));
  }

  // ---------- thông số tự do ----------

  product(id: string): Observable<AdminProduct> {
    return this.http
      .get<Envelope<AdminProduct>>(`${APP_CONFIG.apiBase}/products/${encodeURIComponent(id)}/detail`)
      .pipe(map((r) => toAdminProduct(r.data)));
  }

  saveSpecifications(id: string, version: number, groups: SpecificationGroup[]): Observable<AdminProduct> {
    return this.http
      .put<Envelope<AdminProduct>>(`${APP_CONFIG.apiBase}/products/${encodeURIComponent(id)}/specifications`, {
        version,
        groups,
      })
      .pipe(map((r) => toAdminProduct(r.data)));
  }
}

function toAdminProduct(p: AdminProduct): AdminProduct {
  return { id: p.id, name: p.name, version: p.version, specifications: p.specifications ?? [] };
}
