package com.congthanh.paymentservice.adapter.out.persistence;

import com.congthanh.ecommerce.payment.v1.PaymentAuthorized;
import com.congthanh.ecommerce.payment.v1.PaymentCaptured;
import com.congthanh.ecommerce.payment.v1.PaymentFailed;
import com.congthanh.ecommerce.payment.v1.PaymentRefunded;
import com.congthanh.paymentservice.application.port.out.PaymentRepository;
import com.congthanh.paymentservice.domain.event.PaymentEvent;
import com.congthanh.paymentservice.domain.model.Money;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import com.congthanh.paymentservice.domain.model.PaymentStatus;
import com.congthanh.platform.outbox.OutboxWriter;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class PaymentPersistenceAdapter implements PaymentRepository {

    static final String AGGREGATE = "payment";

    private final PaymentJpaRepository jpa;
    private final OutboxWriter outbox;
    private final PaymentRefundJpaRepository refunds;

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByOrderId(String orderId) {
        return jpa.findByOrderId(orderId).map(PaymentPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Payment> findByProviderRef(String provider, String providerRef) {
        return jpa.findByProviderAndProviderRef(provider, providerRef).map(PaymentPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public void save(Payment p) {
        PaymentEntity e = jpa.findById(p.id()).orElse(null);
        if (e == null) {
            e = new PaymentEntity();
            e.id = p.id();
            e.orderId = p.orderId();
            e.customerId = p.customerId();
            e.amount = p.amount().amount();
            e.currency = p.amount().currency();
            e.method = p.method().name();
            e.createdAt = p.createdAt();
        } else if (e.version != p.version()) {
            throw new ObjectOptimisticLockingFailureException(PaymentEntity.class, p.id());
        }
        e.provider = p.provider();
        e.providerRef = p.providerRef();
        e.status = p.status().name();
        e.redirectUrl = p.redirectUrl();
        e.failureReason = p.failureReason();
        e.authorizationExpiresAt = p.authorizationExpiresAt();
        e.updatedAt = p.updatedAt();
        e.refundedAmount = p.refundedAmount();
        jpa.saveAndFlush(e);
        for (PaymentEvent event : p.pullEvents()) {
            outbox.write(AGGREGATE, p.orderId(), toProto(event));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<RecordedRefund> findRefund(String refundId) {
        return refunds.findById(refundId).map(r -> new RecordedRefund(r.refundId, r.paymentId, r.orderId, r.amount,
                r.currency, r.reason, r.createdAt));
    }

    @Override
    @Transactional
    public void saveRefund(Payment p, RecordedRefund r) {
        PaymentRefundEntity e = new PaymentRefundEntity();
        e.refundId = r.refundId();
        e.paymentId = r.paymentId();
        e.orderId = r.orderId();
        e.amount = r.amount();
        e.currency = r.currency();
        e.reason = r.reason();
        e.createdAt = r.createdAt();
        refunds.saveAndFlush(e);
        save(p);
    }

    static Payment toDomain(PaymentEntity e) {
        return Payment.rehydrate(e.id, e.orderId, e.customerId, new Money(e.amount, e.currency),
                PaymentMethod.valueOf(e.method), e.provider, e.providerRef, PaymentStatus.valueOf(e.status),
                e.redirectUrl, e.failureReason, e.authorizationExpiresAt, e.createdAt, e.updatedAt, e.version,
                e.refundedAmount);
    }

    static Message toProto(PaymentEvent event) {
        return switch (event) {
            case PaymentEvent.PaymentAuthorized a -> PaymentAuthorized.newBuilder()
                    .setPaymentId(a.paymentId()).setOrderId(a.orderId())
                    .setAmount(money(a.amount())).setProvider(a.provider() == null ? "" : a.provider())
                    .setMethod(a.method() == null ? "" : a.method()).build();
            case PaymentEvent.PaymentFailed f -> PaymentFailed.newBuilder()
                    .setOrderId(f.orderId()).setReason(f.reason() == null ? "" : f.reason()).build();
            case PaymentEvent.PaymentCaptured c -> PaymentCaptured.newBuilder()
                    .setPaymentId(c.paymentId()).setOrderId(c.orderId()).setAmount(money(c.amount()))
                    .setProvider(nullToEmpty(c.provider())).setMethod(nullToEmpty(c.method()))
                    .setCustomerId(nullToEmpty(c.customerId())).setProviderRef(nullToEmpty(c.providerRef())).build();
            case PaymentEvent.PaymentRefunded r -> PaymentRefunded.newBuilder()
                    .setPaymentId(r.paymentId()).setOrderId(r.orderId()).setAmount(money(r.amount()))
                    .setRefundId(nullToEmpty(r.refundId())).setReason(nullToEmpty(r.reason()))
                    .setProvider(nullToEmpty(r.provider())).setFullyRefunded(r.fullyRefunded())
                    .setProviderRef(nullToEmpty(r.providerRef())).build();
        };
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static com.congthanh.ecommerce.common.v1.Money money(Money m) {
        BigDecimal amount = m.amount().setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = amount.setScale(0, RoundingMode.DOWN);
        return com.congthanh.ecommerce.common.v1.Money.newBuilder().setCurrencyCode(m.currency())
                .setUnits(units.longValueExact()).setNanos(amount.subtract(units).movePointRight(9).intValueExact())
                .build();
    }
}
