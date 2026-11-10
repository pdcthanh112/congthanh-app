import { bearer, fromGrpcError, grpcClient, resiliencePolicy } from '@ecom/platform';
import {
  InventoryAdjustment_AdjustmentType as AdjustmentType,
  InventoryService,
  InventoryLevel_InventoryStatus as Status,
} from '@ecom/proto/ecommerce/inventory/v1/inventory_service_pb';

export type StockStatus = 'IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK' | 'BACKORDER' | 'DISCONTINUED';

export type AdjustmentKind = keyof typeof AdjustmentType;

export interface StockLevel {
  productId: string;
  available: number;
  reserved: number;
  total: number;
  reorderPoint: number;
  status: StockStatus;
}

const STATUS: Record<Status, StockStatus> = {
  [Status.IN_STOCK]: 'IN_STOCK',
  [Status.LOW_STOCK]: 'LOW_STOCK',
  [Status.OUT_OF_STOCK]: 'OUT_OF_STOCK',
  [Status.BACKORDER]: 'BACKORDER',
  [Status.DISCONTINUED]: 'DISCONTINUED',
};

/** inventory-service (Go) qua gRPC. */
export class InventoryClient {
  private readonly grpc;
  private readonly policy = resiliencePolicy({ timeoutMs: 1_500 });

  constructor(address: string) {
    this.grpc = grpcClient(InventoryService, { address });
  }

  async levels(productIds: readonly string[], token?: string): Promise<Map<string, StockLevel>> {
    const map = new Map<string, StockLevel>();
    if (productIds.length === 0) return map;
    try {
      const res = await this.policy.execute(({ signal }) =>
        this.grpc.getInventoryLevel({ productIds: [...productIds], includeReserved: true }, bearer(token, { signal })),
      );
      for (const l of res.levels) {
        map.set(l.productId, {
          productId: l.productId,
          available: l.availableQuantity,
          reserved: l.reservedQuantity,
          total: l.totalQuantity,
          reorderPoint: l.reorderPoint,
          status: STATUS[l.status] ?? 'OUT_OF_STOCK',
        });
      }
      return map;
    } catch (e) {
      throw fromGrpcError(e, 'inventory-service');
    }
  }

  /** Điều chỉnh tồn (nhập hàng, hỏng, kiểm kê). Không retry: lệnh không idempotent. */
  async adjust(
    a: { productId: string; change: number; kind: AdjustmentKind; reason: string; warehouseId: string },
    userId: string,
    token?: string,
  ) {
    try {
      const res = await this.grpc.adjustInventory(
        {
          adjustments: [
            {
              productId: a.productId,
              quantityChange: a.change,
              warehouseId: a.warehouseId,
              adjustmentType: AdjustmentType[a.kind],
            },
          ],
          reason: a.reason,
          adjustedBy: userId,
          referenceId: `backoffice:${userId}:${Date.now()}`,
        },
        bearer(token, { timeoutMs: 3_000 }),
      );
      const r = res.results[0];
      return {
        success: res.success && !!r?.success,
        newQuantity: r?.newQuantity ?? 0,
        message: r?.errorMessage || res.message,
      };
    } catch (e) {
      throw fromGrpcError(e, 'inventory-service');
    }
  }
}
