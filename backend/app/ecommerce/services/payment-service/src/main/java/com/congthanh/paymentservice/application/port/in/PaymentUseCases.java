package com.congthanh.paymentservice.application.port.in;

import com.congthanh.paymentservice.domain.model.InstallmentPolicy;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** Use case của payment-service. Activity Temporal và webhook/IPN đều đi qua đây. */
public interface PaymentUseCases {

    /** Idempotent theo orderId. Thẻ bị từ chối: PaymentException DECLINED. */
    Authorization authorize(AuthorizeCommand command);

    void capture(String orderId);

    /** Bù trừ: void nếu chưa thu, refund nếu đã thu, không làm gì nếu chưa có payment. */
    void refund(String orderId, String reason);

    /** Kết quả từ cổng (return PayPal, webhook Stripe, sandbox). Idempotent. */
    CallbackOutcome handleCallback(PaymentMethod method, Map<String, String> params);

    Payment getByOrder(String orderId);

    /** Đối tác trả góp dùng được cho số tiền này (đang bật, đúng khoảng, có cổng). Không có lịch trả. */
    List<InstallmentPolicy.Option> installmentOptions(BigDecimal amount, String currency);

    record AuthorizeCommand(String orderId, String customerId, BigDecimal amount, String currency,
                            PaymentMethod method, String paymentToken, String returnUrl, String clientIp) {
    }

    record Authorization(String paymentId, boolean actionRequired, String redirectUrl) {
    }

    /** @param acknowledged cổng có cần nhận phản hồi "đã nhận" không (chữ ký đúng, đơn tồn tại) */
    record CallbackOutcome(boolean acknowledged, String orderId, boolean success, String message) {
    }
}
