import axiosInstance from '@/config/axiosConfig';
import { ok, paged } from '@/graphql/legacy';
import { Product, Response, Supplier } from '@/models/types';

// Gian hàng người bán: supplier-service (Django) qua REST /api/ecommerce/suppliers.
type ApiSupplier = {
  id: string;
  name: string;
  slug: string;
  avatarUrl: string;
  backgroundUrl: string;
  description: string;
};

const toSupplier = (s: ApiSupplier): Supplier => ({
  id: s.id,
  name: s.name,
  slug: s.slug,
  avatar: s.avatarUrl,
  background: s.backgroundUrl,
});

/** id hoặc slug đều được. */
export const getSupplierById = async (id: string): Promise<Response<Supplier>> =>
  ok(toSupplier((await axiosInstance.get(`suppliers/${encodeURIComponent(id)}`)).data.data));

export const getSupplierBySlug = getSupplierById;

/** Sản phẩm của gian hàng: chưa có bộ lọc theo người bán trong supergraph, trả danh sách rỗng. */
export const getProductFromSupplier = async (_id: string, page: number, limit: number) =>
  paged<Product>([], page, limit, 0);
