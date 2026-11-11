import { bearer, fromGrpcError, grpcClient, resiliencePolicy } from '@ecommerce/platform';
import { ProductService } from '@ecommerce/proto/ecommerce/product/v1/product_service_pb';
import type { ProductDirectory } from '../../application/ports.js';

/** Hỏi product-service (Java) qua gRPC; có timeout/retry/circuit breaker. */
export class GrpcProductDirectory implements ProductDirectory {
  private readonly client;
  private readonly policy = resiliencePolicy({ timeoutMs: 2_000 });

  constructor(address: string) {
    this.client = grpcClient(ProductService, { address });
  }

  async sellerOf(productId: string): Promise<string | undefined> {
    try {
      const res = await this.policy.execute(({ signal }) =>
        this.client.getProductsByIds({ productIds: [productId], includeInactive: true }, bearer(undefined, { signal })),
      );
      const seller = res.products[0]?.sellerId;
      return seller || undefined;
    } catch (e) {
      throw fromGrpcError(e, 'product-service');
    }
  }
}
