package com.congthanh.paymentservice.application.port.in;

import com.congthanh.paymentservice.domain.model.InstallmentPolicy;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface PaymentUseCases {

    Authorization authorize(AuthorizeCommand command);

    void capture(String orderId);

    void refund(String orderId, String reason);

    RefundResult refundPartial(PartialRefundCommand command);

    record PartialRefundCommand(String orderId, String refundId, BigDecimal amount, String currency, String reason) {
    }

    record RefundResult(String refundId, String paymentId, BigDecimal amount, String currency, boolean fullyRefunded) {
    }

    CallbackOutcome handleCallback(PaymentMethod method, Map<String, String> params);

    Payment getByOrder(String orderId);

    List<InstallmentPolicy.Option> installmentOptions(BigDecimal amount, String currency);

    record AuthorizeCommand(String orderId, String customerId, BigDecimal amount, String currency,
                            PaymentMethod method, String paymentToken, String returnUrl, String clientIp) {
    }

    record Authorization(String paymentId, boolean actionRequired, String redirectUrl) {
    }

    record CallbackOutcome(boolean acknowledged, String orderId, boolean success, String message) {
    }
}
