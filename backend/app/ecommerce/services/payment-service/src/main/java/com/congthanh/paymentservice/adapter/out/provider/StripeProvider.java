package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import com.stripe.exception.CardException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.ConfirmationToken;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.StripeObject;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCancelParams;
import com.stripe.param.PaymentIntentCaptureParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Stripe PaymentIntent với capture thủ công: authorize giữ tiền, capture sau khi kho commit,
 * cancel để giải phóng. Frontend dùng Payment Element (thẻ, Apple Pay, Google Pay, Link) và gửi lên
 * ConfirmationToken {@code ctoken_...}; token PaymentMethod {@code pm_...} cũ vẫn nhận để tương thích. Mọi request có Idempotency-Key theo orderId nên retry của Temporal an toàn.
 * <p>
 * Trả góp (ADR 0016): Affirm, Klarna, Afterpay cũng là PaymentMethod của Stripe nhưng luôn redirect sang trang
 * đối tác, nên bắt buộc có returnUrl và chỉ nhận ConfirmationToken. Loại phương thức trong token phải khớp method
 * khách chọn, để fraud và báo cáo không bị sai khi client gửi CARD nhưng trả bằng Klarna.
 */
@Component
@ConditionalOnProperty("payment.stripe.api-key")
class StripeProvider implements PaymentProvider {

    private final String apiKey;
    private final String webhookSecret;

    StripeProvider(@Value("${payment.stripe.api-key}") String apiKey,
                   @Value("${payment.stripe.webhook-secret:}") String webhookSecret) {
        this.apiKey = apiKey;
        this.webhookSecret = webhookSecret;
    }

    @Override
    public String name() {
        return "stripe";
    }

    @Override
    public Set<PaymentMethod> methods() {
        return Set.of(PaymentMethod.CARD, PaymentMethod.APPLE_PAY, PaymentMethod.GOOGLE_PAY,
                PaymentMethod.AFFIRM, PaymentMethod.KLARNA, PaymentMethod.AFTERPAY);
    }

    @Override
    public ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp) {
        if (paymentToken == null || paymentToken.isBlank()) {
            return new ProviderResult.Declined("missing Stripe confirmation token");
        }
        boolean installment = payment.method().isInstallment();
        if (installment && returnUrl == null) {
            return new ProviderResult.Declined(payment.method() + " requires returnUrl");
        }
        if (paymentToken.startsWith("ctoken_")) {
            ProviderResult invalid = checkConfirmationToken(payment.method(), paymentToken);
            if (invalid != null) {
                return invalid;
            }
        } else if (installment) {
            return new ProviderResult.Declined(payment.method() + " requires a ConfirmationToken from Payment Element");
        }
        var params = PaymentIntentCreateParams.builder()
                .setAmount(payment.amount().minorUnits())
                .setCurrency(payment.amount().currency().toLowerCase())
                // Không có returnUrl thì chỉ nhận phương thức không cần redirect (thẻ, Apple Pay, Google Pay);
                // 3-D Secure khi đó do Stripe.js xử lý ngay trên trang.
                .setAutomaticPaymentMethods(PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                        .setEnabled(true)
                        .setAllowRedirects(returnUrl == null
                                ? PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.NEVER
                                : PaymentIntentCreateParams.AutomaticPaymentMethods.AllowRedirects.ALWAYS)
                        .build())
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.MANUAL)
                .setConfirm(true)
                .putMetadata("order_id", payment.orderId())
                .putMetadata("payment_id", payment.id());
        if (paymentToken.startsWith("ctoken_")) {
            params.setConfirmationToken(paymentToken);
        } else {
            params.setPaymentMethod(paymentToken);
        }
        if (returnUrl != null) {
            params.setReturnUrl(returnUrl);
        }
        try {
            PaymentIntent pi = PaymentIntent.create(params.build(), options("authorize-" + payment.orderId()));
            return switch (pi.getStatus()) {
                case "requires_capture", "succeeded" -> new ProviderResult.Authorized(pi.getId());
                case "requires_action" -> new ProviderResult.ActionRequired(pi.getId(),
                        pi.getNextAction() != null && pi.getNextAction().getRedirectToUrl() != null
                                ? pi.getNextAction().getRedirectToUrl().getUrl() : null);
                default -> new ProviderResult.Declined("unexpected PaymentIntent status " + pi.getStatus());
            };
        } catch (CardException e) {
            return new ProviderResult.Declined(e.getDeclineCode() != null ? e.getDeclineCode() : e.getCode());
        } catch (StripeException e) {
            throw PaymentException.unavailable("stripe: " + e.getMessage());
        }
    }

    /** null nếu token hợp lệ cho method; Declined nếu thiếu dữ liệu đối tác cần; ném INVALID nếu sai loại. */
    private ProviderResult checkConfirmationToken(PaymentMethod method, String token) {
        ConfirmationToken ct;
        try {
            ct = ConfirmationToken.retrieve(token, options(null));
        } catch (StripeException e) {
            if (e.getStatusCode() != null && e.getStatusCode() == 404) {
                throw PaymentException.invalid("unknown Stripe confirmation token");
            }
            throw PaymentException.unavailable("stripe confirmation token: " + e.getMessage());
        }
        String type = ct.getPaymentMethodPreview() == null ? null : ct.getPaymentMethodPreview().getType();
        if (type == null || !type.equals(method.stripeType())) {
            throw PaymentException.invalid("confirmation token is for " + type + ", not " + method);
        }
        if (method == PaymentMethod.AFTERPAY && ct.getShipping() == null) {
            // Afterpay bắt buộc địa chỉ giao hàng; frontend truyền shipping khi tạo ConfirmationToken.
            return new ProviderResult.Declined("AFTERPAY requires a shipping address");
        }
        return null;
    }

    @Override
    public boolean capture(Payment payment) {
        try {
            PaymentIntent.retrieve(payment.providerRef(), options(null))
                    .capture(PaymentIntentCaptureParams.builder().build(), options("capture-" + payment.orderId()));
            return true;
        } catch (StripeException e) {
            throw PaymentException.unavailable("stripe capture: " + e.getMessage());
        }
    }

    @Override
    public void voidAuthorization(Payment payment) {
        try {
            PaymentIntent.retrieve(payment.providerRef(), options(null))
                    .cancel(PaymentIntentCancelParams.builder().build(), options("void-" + payment.orderId()));
        } catch (StripeException e) {
            throw PaymentException.unavailable("stripe cancel: " + e.getMessage());
        }
    }

    @Override
    public void refund(Payment payment) {
        try {
            Refund.create(RefundCreateParams.builder().setPaymentIntent(payment.providerRef()).build(),
                    options("refund-" + payment.orderId()));
        } catch (StripeException e) {
            throw PaymentException.unavailable("stripe refund: " + e.getMessage());
        }
    }

    /** Webhook (3-D Secure xong): params gồm payload và chữ ký header Stripe-Signature. */
    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        Event event;
        try {
            event = Webhook.constructEvent(params.get("payload"), params.get("signature"), webhookSecret);
        } catch (SignatureVerificationException e) {
            throw PaymentException.invalid("bad Stripe signature");
        }
        StripeObject obj = event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> PaymentException.invalid("unreadable Stripe event"));
        if (!(obj instanceof PaymentIntent pi)) {
            throw PaymentException.invalid("unsupported Stripe event " + event.getType());
        }
        boolean ok = switch (event.getType()) {
            case "payment_intent.amount_capturable_updated", "payment_intent.succeeded" -> true;
            case "payment_intent.payment_failed", "payment_intent.canceled" -> false;
            default -> throw PaymentException.invalid("ignored Stripe event " + event.getType());
        };
        Long amount = ok ? (pi.getAmountCapturable() != null && pi.getAmountCapturable() > 0
                ? pi.getAmountCapturable() : pi.getAmount()) : null;
        return new CallbackResult(pi.getMetadata().get("order_id"), pi.getId(), ok,
                ok ? null : (pi.getLastPaymentError() != null ? pi.getLastPaymentError().getCode() : event.getType()),
                amount);
    }

    private RequestOptions options(String idempotencyKey) {
        var b = RequestOptions.builder().setApiKey(apiKey);
        if (idempotencyKey != null) {
            b.setIdempotencyKey(idempotencyKey);
        }
        return b.build();
    }
}
