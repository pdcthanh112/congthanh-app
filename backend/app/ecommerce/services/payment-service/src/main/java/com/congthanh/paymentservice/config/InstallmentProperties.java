package com.congthanh.paymentservice.config;

import com.congthanh.paymentservice.domain.model.InstallmentPolicy;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code payment.installments}: đối tác trả góp (ADR 0016). Hạn mức mặc định trong application.yaml lấy theo
 * tài liệu Stripe lúc viết, phải đối chiếu lại với tài khoản Stripe trước khi bật ở prod.
 */
@ConfigurationProperties("payment.installments")
record InstallmentProperties(Partner affirm, Partner klarna, Partner afterpay, List<String> currencies) {

    record Partner(boolean enabled, BigDecimal minAmount, BigDecimal maxAmount, Duration authorizationTtl) {
    }

    InstallmentPolicy toPolicy() {
        Map<PaymentMethod, InstallmentPolicy.Limit> limits = new EnumMap<>(PaymentMethod.class);
        put(limits, PaymentMethod.AFFIRM, affirm);
        put(limits, PaymentMethod.KLARNA, klarna);
        put(limits, PaymentMethod.AFTERPAY, afterpay);
        return new InstallmentPolicy(limits, currencies == null ? Set.of() : Set.copyOf(currencies));
    }

    private static void put(Map<PaymentMethod, InstallmentPolicy.Limit> limits, PaymentMethod m, Partner p) {
        if (p != null && p.enabled()) {
            limits.put(m, new InstallmentPolicy.Limit(p.minAmount(), p.maxAmount(), p.authorizationTtl()));
        }
    }
}
