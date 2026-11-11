import { bearer, fromGrpcError, grpcClient, RestClient, resiliencePolicy } from '@ecommerce/platform';
import { ProductService } from '@ecommerce/proto/ecommerce/product/v1/product_service_pb';
import { fromDecimal, type Money } from '@ecommerce/shared';
import { z } from 'zod';

/** Thông tin gọn để hiển thị danh sách/giỏ hàng (gRPC GetProductsByIds, gọi theo lô). */
export interface ProductSummary {
  id: string;
  sku: string;
  name: string;
  price: Money;
  originalPrice: Money;
  active: boolean;
  sellerId: string;
}

const ProductDto = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string().nullish(),
  description: z.string().nullish(),
  thumbnail: z.string().nullish(),
  supplier: z.string().nullish(),
  brand: z.string().nullish(),
  status: z.string().nullish(),
  hasVariant: z.boolean().nullish(),
  category: z.array(z.object({ categoryId: z.string(), displayOrder: z.number().nullish() })).nullish(),
  image: z.array(z.object({ imagePath: z.string(), displayOrder: z.number().nullish() })).nullish(),
  attribute: z
    .array(z.object({ attribute: z.unknown().optional(), value: z.unknown().optional() }).passthrough())
    .nullish(),
});
export type ProductDetail = z.infer<typeof ProductDto>;

const Page = z.object({
  paginationInfo: z.object({ page: z.number(), limit: z.number(), totalPage: z.number(), totalElement: z.number() }),
  responseList: z.array(ProductDto),
});
export type ProductPage = z.infer<typeof Page>;

export interface ProductClientOptions {
  grpcAddress: string;
  restBaseUrl: string;
  currency?: string;
}

/** product-service (Java): gRPC cho tra cứu theo lô, REST cho chi tiết/danh sách (chưa có trên gRPC). */
export class ProductClient {
  private readonly grpc;
  private readonly rest: RestClient;
  private readonly policy = resiliencePolicy({ timeoutMs: 2_000 });
  private readonly currency: string;

  constructor(opts: ProductClientOptions) {
    this.grpc = grpcClient(ProductService, { address: opts.grpcAddress });
    this.rest = new RestClient({ baseUrl: opts.restBaseUrl, name: 'product-service' });
    this.currency = opts.currency ?? 'USD';
  }

  async byIds(ids: readonly string[], includeInactive = false): Promise<Map<string, ProductSummary>> {
    const map = new Map<string, ProductSummary>();
    if (ids.length === 0) return map;
    try {
      // product-service giới hạn 200 id mỗi lần.
      for (let i = 0; i < ids.length; i += 200) {
        const res = await this.policy.execute(({ signal }) =>
          this.grpc.getProductsByIds(
            { productIds: ids.slice(i, i + 200), includeInactive },
            bearer(undefined, { signal }),
          ),
        );
        for (const p of res.products) {
          map.set(p.productId, {
            id: p.productId,
            sku: p.sku,
            name: p.name,
            price: fromDecimal(p.sellingPrice, this.currency),
            originalPrice: fromDecimal(p.originalPrice, this.currency),
            active: p.isActive,
            sellerId: p.sellerId,
          });
        }
      }
      return map;
    } catch (e) {
      throw fromGrpcError(e, 'product-service');
    }
  }

  detail(id: string): Promise<ProductDetail> {
    return this.rest.data(`/api/ecommerce/products/${encodeURIComponent(id)}/detail`, ProductDto);
  }

  bySlug(slug: string): Promise<ProductDetail> {
    return this.rest.data(`/api/ecommerce/products/back-office/slug/${encodeURIComponent(slug)}`, ProductDto);
  }

  list(page: number, limit: number): Promise<ProductPage> {
    return this.rest.data('/api/ecommerce/products', Page, { query: { page, limit } });
  }

  byCategory(categoryId: string, page: number, limit: number): Promise<ProductPage> {
    return this.rest.data(
      `/api/ecommerce/products/store-front/categories/${encodeURIComponent(categoryId)}/products`,
      Page,
      {
        query: { page, limit },
      },
    );
  }

  search(keyword: string): Promise<ProductDetail[]> {
    return this.rest.data('/api/ecommerce/products/search', z.array(ProductDto), { query: { search: keyword } });
  }
}
