package com.congthanh.checkoutservice.adapter.out.http;

import com.congthanh.checkoutservice.application.port.out.FraudCheck;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.FraudVerdict;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POST /internal/fraud/assess của fraud-service với timeout ngắn. fraud-service lỗi/chậm thì mặc định
 * cho qua (fail-open, gắn lý do FRAUD_UNAVAILABLE) để sự cố của nó không chặn bán hàng; đặt
 * {@code checkout.fraud.fail-open=false} để chuyển các đơn đó sang duyệt tay.
 */
@Component
class FraudHttpAdapter implements FraudCheck {

    private static final Logger log = LoggerFactory.getLogger(FraudHttpAdapter.class);

    private final RestClient http;
    private final boolean failOpen;

    FraudHttpAdapter(RestClient.Builder builder,
                     @Value("${checkout.fraud.url:http://localhost:5026}") String url,
                     @Value("${checkout.fraud.timeout:PT0.3S}") Duration timeout,
                     @Value("${checkout.fraud.fail-open:true}") boolean failOpen) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        this.http = builder.baseUrl(url).requestFactory(factory).build();
        this.failOpen = failOpen;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Envelope(Data data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Data(String assessmentId, String decision, int score, List<Reason> reasons) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Reason(String code) {
    }

    /** Phương thức thanh toán của order-service → nhóm phương thức của fraud-service. */
    static String method(String m) {
        return switch (m.toUpperCase(Locale.ROOT)) {
            case "CARD" -> "CARD";
            case "APPLE_PAY", "GOOGLE_PAY" -> "WALLET";
            case "PAYPAL" -> "PAYPAL";
            case "AFFIRM", "KLARNA", "AFTERPAY" -> "BNPL";
            default -> "BANK_TRANSFER";
        };
    }

    static Map<String, Object> body(CheckoutSession s, Context ctx) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("checkoutId", s.id());
        b.put("customerId", s.customerId());
        b.put("amount", s.totals().total().minor());
        b.put("currency", s.currency());
        b.put("paymentMethod", method(ctx.paymentMethod()));
        b.put("email", nz(ctx.email()));
        b.put("phone", nz(ctx.phone()));
        b.put("ip", nz(ctx.ip()));
        b.put("deviceId", nz(ctx.deviceId()));
        var a = s.shippingAddress();
        b.put("shippingAddress", Map.of("countryCode", a.country(), "regionCode", a.city(), "line1", a.line1(), "phone", a.phone()));
        b.put("lines", s.lines().stream().map(l -> Map.of("productId", l.productId(), "quantity", l.quantity(),
                "unitPrice", l.unitPrice().minor())).toList());
        if (ctx.accountCreatedAt() != null) {
            b.put("accountCreatedAt", ctx.accountCreatedAt().toString());
        }
        return b;
    }

    @Override
    public FraudVerdict assess(CheckoutSession s, Context ctx) {
        try {
            Envelope res = http.post().uri("/internal/fraud/assess").body(body(s, ctx)).retrieve().body(Envelope.class);
            Data d = res == null ? null : res.data();
            if (d == null || d.decision() == null) {
                return unavailable("empty response");
            }
            return new FraudVerdict(d.assessmentId(), d.decision(), d.score(),
                    d.reasons() == null ? List.of() : d.reasons().stream().map(Reason::code).toList());
        } catch (RuntimeException e) {
            return unavailable(e.toString());
        }
    }

    private FraudVerdict unavailable(String why) {
        log.warn("fraud-service unavailable ({}), {}", why, failOpen ? "allowing the order" : "sending it to review");
        return new FraudVerdict(null, failOpen ? "APPROVE" : "REVIEW", 0, List.of("FRAUD_UNAVAILABLE"));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

}
