package com.congthanh.checkoutservice.application.port.in;

import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows.Progress;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.Money;

import java.time.Instant;
import java.util.List;

public interface CheckoutUseCase {

    CheckoutSession open(Customer customer, OpenCommand cmd);

    CheckoutSession update(Customer customer, String sessionId, UpdateCommand cmd);

    View get(Customer customer, String sessionId);

    Placement place(Customer customer, String sessionId, PlaceCommand cmd);

    CheckoutSession cancel(Customer customer, String sessionId, String reason);

    /** Kết quả duyệt tay từ fraud-service (event ecommerce.fraud.events). */
    void onFraudReviewDecided(String sessionId, boolean approved, String note);

    /**
     * Khách giành được suất flash sale (event FlashSaleClaimed): mở sẵn phiên checkout cho suất đó. Idempotent theo
     * claim id.
     */
    void onFlashSaleClaimed(FlashSaleClaim claim);

    record FlashSaleClaim(String claimId, String saleId, String productId, String customerId, int quantity,
                          Instant expiresAt) {
    }

    record Customer(String id, List<String> groups, String email, Instant accountCreatedAt, boolean staff) {
        public boolean canAccess(CheckoutSession s) {
            return staff || s.isOwnedBy(id);
        }
    }

    record OpenCommand(List<CheckoutSession.Item> items, String currency, String promotionCode, Address shippingAddress,
                       Address billingAddress, String note) {
    }

    /** Field null = giữ nguyên. {@code clearPromotion} để bỏ voucher. */
    record UpdateCommand(List<CheckoutSession.Item> items, String promotionCode, boolean clearPromotion,
                         Address shippingAddress, Address billingAddress, String shippingCarrier,
                         String shippingServiceCode, String note) {
    }

    record PlaceCommand(String paymentMethod, String paymentToken, String returnUrl, String clientIp, String deviceId,
                        Money expectedTotal) {
    }

    record View(CheckoutSession session, Progress progress) {
    }

    record Placement(CheckoutSession session, Outcome outcome) {
    }

    enum Outcome { PLACED, PENDING_REVIEW }
}
