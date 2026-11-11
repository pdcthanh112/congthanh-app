import { DomainError } from "@ecommerce/platform/errors";
import { slugify } from "./slug.js";

export type CategoryStatus = "ACTIVE" | "INACTIVE";

export interface CategoryProps {
  id: string;
  name: string;
  slug: string;
  description: string | null;
  image: string | null;
  parentId: string | null;
  status: CategoryStatus;
  sortOrder: number;
  createdAt: Date;
  updatedAt: Date;
  version: number;
}

export interface CategoryDraft {
  name: string;
  slug?: string | null | undefined;
  description?: string | null | undefined;
  image?: string | null | undefined;
  parentId?: string | null | undefined;
  sortOrder?: number | undefined;
}

const MAX_DEPTH = 5;

export class Category {
  private constructor(private props: CategoryProps) {}

  static create(id: string, draft: CategoryDraft, now: Date): Category {
    const name = requireName(draft.name);
    return new Category({
      id,
      name,
      slug: normalizeSlug(draft.slug, name),
      description: draft.description ?? null,
      image: draft.image ?? null,
      parentId: draft.parentId ?? null,
      status: "ACTIVE",
      sortOrder: draft.sortOrder ?? 0,
      createdAt: now,
      updatedAt: now,
      version: 0,
    });
  }

  static rehydrate(props: CategoryProps): Category {
    return new Category({ ...props });
  }

  rename(draft: Omit<CategoryDraft, "parentId">, now: Date): void {
    const name = requireName(draft.name);
    this.props = {
      ...this.props,
      name,
      slug: normalizeSlug(draft.slug, name),
      description: draft.description === undefined ? this.props.description : draft.description,
      image: draft.image === undefined ? this.props.image : draft.image,
      sortOrder: draft.sortOrder ?? this.props.sortOrder,
      updatedAt: now,
    };
  }

  /**
   * Chuyển sang cha mới. `ancestorsOfNewParent` là đường dẫn từ gốc tới cha mới (gồm cả cha mới),
   * dùng để chặn vòng (đưa danh mục vào nhánh con của chính nó) và giới hạn độ sâu.
   */
  moveTo(newParentId: string | null, ancestorsOfNewParent: readonly string[], now: Date): void {
    if (newParentId === this.props.id || ancestorsOfNewParent.includes(this.props.id)) {
      throw DomainError.invalid("a category cannot be moved under itself or its descendants", "CATEGORY_CYCLE");
    }
    if (ancestorsOfNewParent.length + 1 > MAX_DEPTH) {
      throw DomainError.invalid(`category tree is limited to ${MAX_DEPTH} levels`, "CATEGORY_TOO_DEEP");
    }
    this.props = { ...this.props, parentId: newParentId, updatedAt: now };
  }

  deactivate(now: Date): void {
    if (this.props.status === "INACTIVE") return;
    this.props = { ...this.props, status: "INACTIVE", updatedAt: now };
  }

  activate(now: Date): void {
    if (this.props.status === "ACTIVE") return;
    this.props = { ...this.props, status: "ACTIVE", updatedAt: now };
  }

  get id() {
    return this.props.id;
  }
  get parentId() {
    return this.props.parentId;
  }
  get status() {
    return this.props.status;
  }
  get slug() {
    return this.props.slug;
  }
  snapshot(): Readonly<CategoryProps> {
    return { ...this.props };
  }
}

export function checkDepth(ancestorsOfParent: readonly string[]): void {
  if (ancestorsOfParent.length + 1 > MAX_DEPTH) {
    throw DomainError.invalid(`category tree is limited to ${MAX_DEPTH} levels`, "CATEGORY_TOO_DEEP");
  }
}

function requireName(name: string): string {
  const n = name?.trim();
  if (!n) throw DomainError.invalid("name is required");
  if (n.length > 120) throw DomainError.invalid("name is at most 120 characters");
  return n;
}

function normalizeSlug(slug: string | null | undefined, name: string): string {
  const s = slugify(slug?.trim() || name);
  if (!s) throw DomainError.invalid("slug is empty after normalisation");
  return s;
}
