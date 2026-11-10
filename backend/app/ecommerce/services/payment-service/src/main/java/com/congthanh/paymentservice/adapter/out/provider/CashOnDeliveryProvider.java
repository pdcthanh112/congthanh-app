package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.Set;

/** COD: không có tiền để giữ; "authorize" luôn được, thu tiền khi giao hàng (ngoài saga checkout). */
@Component
class CashOnDeliveryProvider implements PaymentProvider {

    @Override
    public String name() {
        return "cod";
    }

    @Override
    public Set<PaymentMethod> methods() {
        return Set.of(PaymentMethod.CASH_ON_DELIVERY);
    }

    @Override
    public ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp) {
        return new ProviderResult.Authorized("cod-" + payment.orderId());
    }

    @Override
    public boolean capture(Payment payment) {
        return false;
    }

    @Override
    public void voidAuthorization(Payment payment) {
    }

    @Override
    public void refund(Payment payment) {
    }
}
