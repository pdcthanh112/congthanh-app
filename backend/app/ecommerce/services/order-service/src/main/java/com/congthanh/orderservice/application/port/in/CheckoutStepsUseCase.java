package com.congthanh.orderservice.application.port.in;

/**
 * Các bước của saga checkout, do activity Temporal gọi. Mọi method idempotent theo orderId
 * và tự đọc dữ liệu đơn từ DB, nên workflow chỉ cần mang theo orderId.
 * Lỗi nghiệp vụ (hết hàng, voucher hết lượt) ném OrderException kind REJECTED.
 */
public interface CheckoutStepsUseCase {

    void redeemPromotion(String orderId);

    void cancelPromotionRedemption(String orderId, String reason);

    /** @return reservation id ở inventory-service */
    String reserveInventory(String orderId);

    void releaseInventory(String orderId, String reason);

    void commitInventory(String orderId);

    void confirm(String orderId, String paymentId);

    void cancel(String orderId, String reason);
}
