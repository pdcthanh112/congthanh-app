import { bearer, fromGrpcError, grpcClient, resiliencePolicy } from '@ecommerce/platform';
import { PromotionService, PromotionType } from '@ecommerce/proto/ecommerce/promotion/v1/promotion_service_pb';
import { fromProtoMoney, type Money, toProtoMoney } from '@ecommerce/shared';

export interface DiscountQuote {
  valid: boolean;
  discount: Money | null;
  reason: string | null;
  promotionId: string | null;
}

export interface PromotionInfo {
  id: string;
  code: string;
  type: keyof typeof PromotionType;
  value: number;
  usageLimit: number;
  description: string;
  status: string;
  startDate: Date | null;
  endDate: Date | null;
}

/** promotion-service (Go) qua gRPC: báo giá giảm giá (không giữ chỗ), tra cứu khuyến mãi. */
export class PromotionClient {
  private readonly grpc;
  private readonly policy = resiliencePolicy({ timeoutMs: 1_500 });

  constructor(address: string) {
    this.grpc = grpcClient(PromotionService, { address });
  }

  async quote(code: string, customerId: string, subtotal: Money): Promise<DiscountQuote> {
    try {
      const r = await this.policy.execute(({ signal }) =>
        this.grpc.quoteDiscount({ code, customerId, subtotal: toProtoMoney(subtotal) }, bearer(undefined, { signal })),
      );
      return {
        valid: r.valid,
        discount: r.discount ? fromProtoMoney(r.discount) : null,
        reason: r.reason || null,
        promotionId: r.promotionId || null,
      };
    } catch (e) {
      throw fromGrpcError(e, 'promotion-service');
    }
  }

  async byCode(code: string, token?: string): Promise<PromotionInfo> {
    try {
      const p = await this.policy.execute(({ signal }) =>
        this.grpc.getPromotionByCode({ code }, bearer(token, { signal })),
      );
      return {
        id: String(p.id),
        code: p.code,
        type: PromotionType[p.type] as keyof typeof PromotionType,
        value: p.value,
        usageLimit: p.usageLimit,
        description: p.description,
        status: p.status,
        startDate: p.startDate ? new Date(Number(p.startDate.seconds) * 1000) : null,
        endDate: p.endDate ? new Date(Number(p.endDate.seconds) * 1000) : null,
      };
    } catch (e) {
      throw fromGrpcError(e, 'promotion-service');
    }
  }
}
