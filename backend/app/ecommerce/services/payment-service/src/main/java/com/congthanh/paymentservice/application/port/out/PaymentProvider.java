package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;

import java.util.Map;
import java.util.Set;

/**
 * Một cổng thanh toán (Stripe, PayPal, sandbox). Mọi lệnh gửi idempotency key
 * suy ra từ orderId để retry không tạo giao dịch thứ hai phía cổng.
 */
public interface PaymentProvider {

    String name();

    Set<PaymentMethod> methods();

    ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp);

    /** @return true nếu đã thu tiền; false nếu cổng không cần bước thu riêng (sandbox). */
    boolean capture(Payment payment);

    void voidAuthorization(Payment payment);

    void refund(Payment payment);

    /** Kiểm tra chữ ký callback và rút ra kết quả; chữ ký sai ném PaymentException INVALID. */
    default CallbackResult verifyCallback(Map<String, String> params) {
        throw new UnsupportedOperationException(name() + " has no callbacks");
    }

    sealed interface ProviderResult {
        record Authorized(String providerRef) implements ProviderResult {
        }

        record ActionRequired(String providerRef, String redirectUrl) implements ProviderResult {
        }

        record Declined(String reason) implements ProviderResult {
        }
    }

    /** @param amountMinor số tiền cổng báo đã thu (đơn vị nhỏ nhất), null nếu cổng không gửi */
    record CallbackResult(String orderId, String providerRef, boolean success, String reason, Long amountMinor) {
    }
}
