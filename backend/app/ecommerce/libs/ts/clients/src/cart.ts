import { Code, ConnectError } from '@connectrpc/connect';
import { bearer, DomainError, fromGrpcError, grpcClient, RestClient, resiliencePolicy } from '@ecommerce/platform';
import { CartService } from '@ecommerce/proto/ecommerce/cart/v1/cart_service_pb';
import { z } from 'zod';

export interface CartView {
  id: number;
  customerId: string;
  items: { id: number; productId: string; quantity: number; addedAt: Date | null }[];
}

const Item = z.object({ id: z.number(), product: z.string(), quantity: z.number() });
const CartDto = z.object({ id: z.number() });

/** cart-service (Go): đọc qua gRPC, ghi qua REST (kiểm tra chủ sở hữu bằng token người dùng). */
export class CartClient {
  private readonly grpc;
  private readonly rest: RestClient;
  private readonly policy = resiliencePolicy({ timeoutMs: 1_500 });

  constructor(opts: { grpcAddress: string; restBaseUrl: string }) {
    this.grpc = grpcClient(CartService, { address: opts.grpcAddress });
    this.rest = new RestClient({ baseUrl: opts.restBaseUrl, name: 'cart-service' });
  }

  /** Giỏ mặc định của khách; chưa có thì undefined. */
  async defaultCart(customerId: string, token: string): Promise<CartView | undefined> {
    try {
      const c = await this.policy.execute(({ signal }) =>
        this.grpc.getDefaultCart({ customerId }, bearer(token, { signal })),
      );
      return {
        id: Number(c.id),
        customerId: c.customerId,
        items: c.items.map((i) => ({
          id: Number(i.id),
          productId: i.productId,
          quantity: i.quantity,
          addedAt: i.createdAt ? new Date(Number(i.createdAt.seconds) * 1000) : null,
        })),
      };
    } catch (e) {
      if (e instanceof ConnectError && e.code === Code.NotFound) return undefined;
      throw fromGrpcError(e, 'cart-service');
    }
  }

  /** Lấy hoặc tạo giỏ mặc định (giỏ đầu tiên của khách tự là mặc định). */
  async ensureDefault(customerId: string, token: string): Promise<number> {
    const existing = await this.defaultCart(customerId, token);
    if (existing) return existing.id;
    const created = await this.rest.data('/api/ecommerce/carts/', CartDto, {
      method: 'POST',
      body: { name: 'Giỏ hàng', isDefault: true },
      token,
    });
    return created.id;
  }

  async addItem(token: string, cartId: number, productId: string, quantity: number) {
    if (quantity < 1 || quantity > 999) throw DomainError.invalid('quantity must be between 1 and 999');
    return this.rest.data(`/api/ecommerce/carts/${cartId}/items`, Item, {
      method: 'POST',
      body: { productId, quantity },
      token,
    });
  }

  async updateItem(token: string, cartId: number, itemId: number, quantity: number) {
    return this.rest.data(`/api/ecommerce/carts/${cartId}/items/${itemId}`, Item, {
      method: 'PUT',
      query: { quantity },
      token,
    });
  }

  async removeItem(token: string, cartId: number, itemId: number) {
    await this.rest.call(`/api/ecommerce/carts/${cartId}/items/${itemId}`, z.unknown(), {
      method: 'DELETE',
      token,
      idempotent: true,
    });
  }

  async clear(token: string, cartId: number) {
    await this.rest.call(`/api/ecommerce/carts/${cartId}/items`, z.unknown(), {
      method: 'DELETE',
      token,
      idempotent: true,
    });
  }
}
