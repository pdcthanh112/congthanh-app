/**
 * Bản ghi "đã mua" lấy từ event đơn hàng: OrderPlaced tạo PENDING, OrderConfirmed (đã thanh toán) → CONFIRMED,
 * OrderCancelled → CANCELLED. Chỉ CONFIRMED mới được đánh giá ("đã mua hàng" được xác minh).
 */
export type PurchaseStatus = 'PENDING' | 'CONFIRMED' | 'CANCELLED';

export interface Purchase {
  orderId: string;
  customerId: string;
  productId: string;
  status: PurchaseStatus;
  confirmedAt: Date | null;
}
