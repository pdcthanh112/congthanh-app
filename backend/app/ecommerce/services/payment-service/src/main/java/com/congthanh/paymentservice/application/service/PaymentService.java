package com.congthanh.paymentservice.application.service;

import com.congthanh.paymentservice.application.port.in.PaymentUseCases;
import com.congthanh.paymentservice.application.port.out.CheckoutNotifier;
import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.application.port.out.PaymentProvider.ProviderResult;
import com.congthanh.paymentservice.application.port.out.PaymentProviders;
import com.congthanh.paymentservice.application.port.out.PaymentRepository;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Money;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import com.congthanh.paymentservice.domain.model.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

/**
 * Gọi cổng thanh toán NGOÀI transaction DB (không giữ connection khi chờ mạng), và ghi trạng thái
 * trước/sau mỗi lần gọi để lần retry biết đã tới bước nào.
 */
@Service
public class PaymentService implements PaymentUseCases {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final PaymentRepository payments;
    private final PaymentProviders providers;
    private final CheckoutNotifier checkout;
    private final TransactionTemplate tx;
    private final Clock clock;

    public PaymentService(PaymentRepository payments, PaymentProviders providers, CheckoutNotifier checkout,
                          TransactionTemplate tx, Clock clock) {
        this.payments = payments;
        this.providers = providers;
        this.checkout = checkout;
        this.tx = tx;
        this.clock = clock;
    }

    @Override
    public Authorization authorize(AuthorizeCommand cmd) {
        PaymentProvider provider = providers.forMethod(cmd.method());
        Payment payment = payments.findByOrderId(cmd.orderId()).orElseGet(() -> create(cmd, provider));

        switch (payment.status()) {
            case AUTHORIZED, CAPTURED -> {
                return new Authorization(payment.id(), false, null);
            }
            case PENDING_ACTION -> {
                return new Authorization(payment.id(), true, payment.redirectUrl());
            }
            case DECLINED -> throw PaymentException.declined(payment.failureReason());
            case VOIDED, REFUNDED -> throw PaymentException.conflict("payment for order " + cmd.orderId() + " was cancelled");
            case INITIATED -> {
                // tiếp tục bên dưới: lần đầu, hoặc lần trước chết sau khi tạo bản ghi
            }
        }

        ProviderResult result = provider.authorize(payment, cmd.paymentToken(), cmd.returnUrl(), cmd.clientIp());
        Payment updated = mutate(payment.orderId(), p -> {
            switch (result) {
                case ProviderResult.Authorized a -> p.authorized(a.providerRef(), clock.instant());
                case ProviderResult.ActionRequired a -> p.actionRequired(a.providerRef(), a.redirectUrl(), clock.instant());
                case ProviderResult.Declined d -> p.declined(d.reason(), clock.instant());
            }
        });
        if (result instanceof ProviderResult.Declined d) {
            throw PaymentException.declined(d.reason());
        }
        return new Authorization(updated.id(), updated.status() == PaymentStatus.PENDING_ACTION, updated.redirectUrl());
    }

    @Override
    public void capture(String orderId) {
        Payment payment = load(orderId);
        if (payment.status() == PaymentStatus.CAPTURED) {
            return;
        }
        if (payment.status() != PaymentStatus.AUTHORIZED) {
            throw PaymentException.conflict("cannot capture payment in status " + payment.status());
        }
        if (providers.byName(payment.provider()).capture(payment)) {
            mutate(orderId, p -> p.captured(clock.instant()));
        }
    }

    @Override
    public void refund(String orderId, String reason) {
        Payment payment = payments.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            return; // saga bù trừ trước khi authorize kịp tạo payment
        }
        PaymentProvider provider = providers.byName(payment.provider());
        switch (payment.status()) {
            case INITIATED, PENDING_ACTION -> mutate(orderId, p -> p.voided(reason, clock.instant()));
            case AUTHORIZED -> {
                provider.voidAuthorization(payment);
                mutate(orderId, p -> p.voided(reason, clock.instant()));
            }
            case CAPTURED -> {
                provider.refund(payment);
                mutate(orderId, p -> p.refunded(clock.instant()));
            }
            case DECLINED, VOIDED, REFUNDED -> {
                // không còn tiền nào để trả
            }
        }
    }

    @Override
    public CallbackOutcome handleCallback(PaymentMethod method, Map<String, String> params) {
        PaymentProvider provider = providers.forMethod(method);
        PaymentProvider.CallbackResult raw = provider.verifyCallback(params);
        Payment payment = (raw.orderId() != null ? payments.findByOrderId(raw.orderId())
                : payments.findByProviderRef(provider.name(), raw.providerRef()))
                .orElseThrow(() -> PaymentException.notFound("no payment for callback " + raw.providerRef()));
        // Không tin số tiền trong callback: phải khớp đúng số tiền đã yêu cầu.
        PaymentProvider.CallbackResult cb = raw.success() && raw.amountMinor() != null
                && raw.amountMinor() != payment.amount().minorUnits()
                ? new PaymentProvider.CallbackResult(raw.orderId(), raw.providerRef(), false, "AMOUNT_MISMATCH", raw.amountMinor())
                : raw;

        switch (payment.status()) {
            case PENDING_ACTION, INITIATED -> {
                Payment updated = mutate(payment.orderId(), p -> {
                    if (!cb.success()) {
                        p.declined(cb.reason(), clock.instant());
                    } else if (provider.capturesImmediately()) {
                        p.paidImmediately(cb.providerRef(), clock.instant());
                    } else {
                        p.authorized(cb.providerRef(), clock.instant());
                    }
                });
                checkout.paymentCompleted(updated.orderId(), updated.id(), cb.success(), cb.reason());
            }
            case VOIDED -> {
                // Khách trả SAU khi saga đã huỷ (hết 15 phút): tiền đã bị trừ thì hoàn ngay.
                if (cb.success()) {
                    log.warn("late payment for cancelled order {}, refunding", payment.orderId());
                    provider.refund(payment);
                    mutate(payment.orderId(), p -> p.refunded(clock.instant()));
                }
            }
            default -> {
                // Callback lặp lại: trạng thái đã chốt. Gửi lại signal là vô hại (workflow chỉ nhận lần đầu).
                boolean ok = payment.status() == PaymentStatus.AUTHORIZED || payment.status() == PaymentStatus.CAPTURED;
                checkout.paymentCompleted(payment.orderId(), payment.id(), ok, payment.failureReason());
            }
        }
        return new CallbackOutcome(true, payment.orderId(), cb.success(), cb.reason());
    }

    @Override
    public Payment getByOrder(String orderId) {
        return load(orderId);
    }

    private Payment create(AuthorizeCommand cmd, PaymentProvider provider) {
        Payment p = Payment.initiate(UUID.randomUUID().toString(), cmd.orderId(), cmd.customerId(),
                new Money(cmd.amount(), cmd.currency()), cmd.method(), provider.name(), clock.instant());
        try {
            tx.executeWithoutResult(s -> payments.save(p));
            return p;
        } catch (DataIntegrityViolationException e) {
            // Hai lần gọi song song cho cùng đơn: bên thua đọc bản ghi bên thắng đã tạo.
            return load(cmd.orderId());
        }
    }

    private Payment mutate(String orderId, java.util.function.Consumer<Payment> change) {
        return tx.execute(s -> {
            Payment p = load(orderId);
            change.accept(p);
            payments.save(p);
            return p;
        });
    }

    private Payment load(String orderId) {
        return payments.findByOrderId(orderId)
                .orElseThrow(() -> PaymentException.notFound("no payment for order " + orderId));
    }
}
