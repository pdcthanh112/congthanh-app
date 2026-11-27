import { bearer, fromGrpcError, grpcClient, RestClient, resiliencePolicy } from '@ecommerce/platform';
import { ProductService } from '@ecommerce/proto/ecommerce/product/v1/product_service_pb';
import { fromProtoMoney, type Money } from '@ecommerce/shared';
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
  /** Có giá trị khi sản phẩm là một biến thể: id của sản phẩm cha. */
  parentId: z.string().nullish(),
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
    .array(
      z
        .object({
          attribute: z.string().nullish(),
          attributeId: z.number().nullish(),
          value: z.string().nullish(),
          valueNumber: z.number().nullish(),
          unit: z.string().nullish(),
          /** Nhóm hiển thị (ADR 0033); null = không nhóm. product-service đã sắp theo nhóm rồi thứ tự thuộc tính. */
          groupName: z.string().nullish(),
        })
        .passthrough(),
    )
    .nullish(),
  /** Thông số tự do chỉ để hiển thị (ADR 0033); chỉ có ở API chi tiết. */
  specifications: z
    .array(
      z.object({
        name: z.string().nullish(),
        items: z.array(z.object({ name: z.string(), value: z.string() })),
      }),
    )
    .nullish(),
});
export type ProductDetail = z.infer<typeof ProductDto>;

const VariantDto = z.object({
  id: z.string(),
  name: z.string(),
  slug: z.string().nullish(),
  sku: z.string().nullish(),
  price: z.number().nullish(),
  currency: z.string().nullish(),
  thumbnail: z.object({ imagePath: z.string() }).nullish(),
  image: z.array(z.object({ imagePath: z.string() })).nullish(),
  options: z.record(z.string(), z.string()).nullish(),
});
export type ProductVariant = z.infer<typeof VariantDto>;

const OptionValueDto = z.object({
  id: z.number(),
  productOptionId: z.number(),
  productOptionName: z.string(),
  productOptionValue: z.string(),
});
export type ProductOptionValue = z.infer<typeof OptionValueDto>;

const Page = z.object({
  paginationInfo: z.object({ page: z.number(), limit: z.number(), totalPage: z.number(), totalElement: z.number() }),
  responseList: z.array(ProductDto),
});
export type ProductPage = z.infer<typeof Page>;

export interface ProductClientOptions {
  grpcAddress: string;
  restBaseUrl: string;
}

export class ProductClient {
  private readonly grpc;
  private readonly rest: RestClient;
  private readonly policy = resiliencePolicy({ timeoutMs: 2_000 });

  constructor(opts: ProductClientOptions) {
    this.grpc = grpcClient(ProductService, { address: opts.grpcAddress });
    this.rest = new RestClient({ baseUrl: opts.restBaseUrl, name: 'product-service' });
  }

  async byIds(ids: readonly string[], includeInactive = false): Promise<Map<string, ProductSummary>> {
    const map = new Map<string, ProductSummary>();
    if (ids.length === 0) return map;
    try {
      // product-service giới hạn 200 id mỗi lần.
      for (let i = 0; i < ids.length; i += 200) {
        const res = await this.policy.execute(({ signal }) => this.grpc.getProductsByIds({ productIds: ids.slice(i, i + 200), includeInactive }, bearer(undefined, { signal })));
        for (const p of res.products) {
          if (!p.sellingPriceMoney?.currencyCode || !p.originalPriceMoney?.currencyCode) continue;
          map.set(p.productId, {
            id: p.productId,
            sku: p.sku,
            name: p.name,
            price: fromProtoMoney(p.sellingPriceMoney),
            originalPrice: fromProtoMoney(p.originalPriceMoney),
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
    return this.rest.data(`/api/ecommerce/products/store-front/slug/${encodeURIComponent(slug)}`, ProductDto);
  }

  variants(parentId: string): Promise<ProductVariant[]> {
    return this.rest.call(`/api/ecommerce/products/storefront/product-variations/${encodeURIComponent(parentId)}`, z.array(VariantDto));
  }

  optionValues(productId: string): Promise<ProductOptionValue[]> {
    return this.rest.data(`/api/ecommerce/products/storefront/product-option-combinations/${encodeURIComponent(productId)}/values`, z.array(OptionValueDto));
  }

  list(page: number, limit: number): Promise<ProductPage> {
    return this.rest.data('/api/ecommerce/products', Page, { query: { page, limit } });
  }

  byCategory(categoryId: string, page: number, limit: number): Promise<ProductPage> {
    return this.rest.data(`/api/ecommerce/products/store-front/categories/${encodeURIComponent(categoryId)}/products`, Page, {
      query: { page, limit },
    });
  }

  search(keyword: string): Promise<ProductDetail[]> {
    return this.rest.data('/api/ecommerce/products/search', z.array(ProductDto), { query: { search: keyword } });
  }
}
