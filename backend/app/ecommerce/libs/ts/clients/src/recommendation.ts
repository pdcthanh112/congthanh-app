import { RestClient } from '@ecommerce/platform';
import { z } from 'zod';

const Item = z.object({ productId: z.string(), score: z.number(), reason: z.string() });
export type Recommendation = z.infer<typeof Item>;
export type RecommendationKind = 'BOUGHT_TOGETHER' | 'SIMILAR';

/**
 * recommendation-service (FastAPI) qua REST. Gợi ý chỉ để hiển thị: client này nuốt lỗi và trả danh sách rỗng
 * để trang sản phẩm không hỏng khi service gợi ý chậm hoặc sập.
 */
export class RecommendationClient {
  private readonly rest: RestClient;

  constructor(baseUrl: string) {
    this.rest = new RestClient({ baseUrl, name: 'recommendation-service', timeoutMs: 800 });
  }

  forProduct(productId: string, kind: RecommendationKind, limit: number) {
    const path = kind === 'SIMILAR' ? 'similar' : 'bought-together';
    return this.list(`/api/ecommerce/recommendations/products/${encodeURIComponent(productId)}/${path}`, { limit });
  }

  trending(limit: number, categoryId?: string | null) {
    return this.list('/api/ecommerce/recommendations/trending', { limit, ...(categoryId ? { categoryId } : {}) });
  }

  /** Có token thì cá nhân hoá theo khách; không có thì theo sessionId (X-Session-Id) hoặc xu hướng. */
  forYou(limit: number, token?: string, sessionId?: string) {
    return this.list('/api/ecommerce/recommendations/for-you', { limit }, token, sessionId);
  }

  /** Ghi tín hiệu xem/thêm giỏ; lỗi bị bỏ qua. */
  async track(type: 'VIEW' | 'CART', productId: string, token?: string, sessionId?: string): Promise<void> {
    try {
      await this.rest.call('/api/ecommerce/recommendations/events', z.unknown(), {
        method: 'POST',
        body: { type, productId },
        ...(token ? { token } : {}),
        ...(sessionId ? { headers: { 'x-session-id': sessionId } } : {}),
      });
    } catch {
      /* chỉ là tín hiệu */
    }
  }

  private async list(
    path: string,
    query: Record<string, string | number>,
    token?: string,
    sessionId?: string,
  ): Promise<Recommendation[]> {
    try {
      return await this.rest.data(path, z.array(Item), {
        query,
        ...(token ? { token } : {}),
        ...(sessionId ? { headers: { 'x-session-id': sessionId } } : {}),
      });
    } catch {
      return [];
    }
  }
}
