package com.congthanh.paymentservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.payment.AuthorizePaymentRequest;
import com.congthanh.ecommerce.workflow.payment.PaymentActivities;
import com.congthanh.ecommerce.workflow.payment.PaymentAuthorization;
import com.congthanh.ecommerce.workflow.payment.PaymentFailures;
import com.congthanh.ecommerce.workflow.payment.RefundPaymentRequest;
import com.congthanh.paymentservice.application.port.in.PaymentUseCases;
import com.congthanh.paymentservice.application.port.in.PaymentUseCases.AuthorizeCommand;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

/**
 * Activity Payment.* trên task queue "payment". Bị từ chối/không hỗ trợ → lỗi không retry để saga bù trừ;
 * cổng lỗi mạng (UNAVAILABLE) → ném nguyên để Temporal retry theo backoff của workflow.
 */
@Component
public class PaymentActivitiesImpl implements PaymentActivities {

    private final PaymentUseCases payments;

    public PaymentActivitiesImpl(PaymentUseCases payments) {
        this.payments = payments;
    }

    @Override
    public PaymentAuthorization authorize(AuthorizePaymentRequest r) {
        try {
            PaymentMethod method;
            try {
                method = PaymentMethod.valueOf(r.method());
            } catch (IllegalArgumentException e) {
                throw PaymentException.unsupported("unknown payment method " + r.method());
            }
            var auth = payments.authorize(new AuthorizeCommand(r.orderId(), r.customerId(), r.amount(), r.currency(),
                    method, r.paymentToken(), r.returnUrl(), r.clientIp()));
            return new PaymentAuthorization(auth.paymentId(),
                    auth.actionRequired() ? PaymentAuthorization.Status.ACTION_REQUIRED : PaymentAuthorization.Status.AUTHORIZED,
                    auth.redirectUrl());
        } catch (PaymentException e) {
            throw translate(e);
        }
    }

    @Override
    public void capture(String orderId) {
        try {
            payments.capture(orderId);
        } catch (PaymentException e) {
            throw translate(e);
        }
    }

    @Override
    public void refund(RefundPaymentRequest r) {
        try {
            payments.refund(r.orderId(), r.reason());
        } catch (PaymentException e) {
            throw translate(e);
        }
    }

    static RuntimeException translate(PaymentException e) {
        return switch (e.kind()) {
            case DECLINED -> ApplicationFailure.newNonRetryableFailure(e.getMessage(), PaymentFailures.DECLINED);
            case UNSUPPORTED, INVALID -> ApplicationFailure.newNonRetryableFailure(e.getMessage(), PaymentFailures.UNSUPPORTED_METHOD);
            default -> e;
        };
    }
}
