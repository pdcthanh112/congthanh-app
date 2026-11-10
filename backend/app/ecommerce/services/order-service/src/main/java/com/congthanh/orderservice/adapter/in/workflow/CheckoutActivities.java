package com.congthanh.orderservice.adapter.in.workflow;

import io.temporal.activity.ActivityInterface;

/** Các bước chạy trong order-service (task queue checkout). Mỏng: chỉ chuyển sang CheckoutStepsUseCase. */
@ActivityInterface(namePrefix = "Checkout.")
public interface CheckoutActivities {

    void redeemPromotion(String orderId);

    void cancelPromotionRedemption(String orderId, String reason);

    String reserveInventory(String orderId);

    void releaseInventory(String orderId, String reason);

    void commitInventory(String orderId);

    void confirmOrder(String orderId, String paymentId);

    void cancelOrder(String orderId, String reason);
}
