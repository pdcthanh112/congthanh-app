package com.congthanh.ecommerce.workflow.checkout;

import io.temporal.activity.ActivityInterface;

@ActivityInterface(namePrefix = "Checkout.")
public interface CheckoutActivities {

    /** Kiểu ApplicationFailure cho lỗi nghiệp vụ (hết hàng, voucher không hợp lệ...): workflow bù trừ, không retry. */
    String REJECTED = "CheckoutRejected";

    void redeemPromotion(String orderId);

    void cancelPromotionRedemption(String orderId, String reason);

    String reserveInventory(String orderId);

    void releaseInventory(String orderId, String reason);

    void commitInventory(String orderId);

    void confirmOrder(String orderId, String paymentId);

    void cancelOrder(String orderId, String reason);
}
