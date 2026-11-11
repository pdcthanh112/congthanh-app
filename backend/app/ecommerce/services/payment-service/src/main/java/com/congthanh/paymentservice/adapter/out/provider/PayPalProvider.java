package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * PayPal Orders API v2 (intent AUTHORIZE), gọi bằng RestClient thay cho SDK v1 đã ngừng hỗ trợ.
 * providerRef = PayPal order id; id của authorization/capture luôn đọc lại từ order nên không phải lưu thêm.
 * Mọi POST có header PayPal-Request-Id theo orderId để PayPal tự chống trùng.
 */
@Component
@ConditionalOnProperty("payment.paypal.client-id")
class PayPalProvider implements PaymentProvider {

    private final RestClient http;
    private final String clientId;
    private final String clientSecret;
    private final String callbackBase;
    private final String brandName;
    private volatile String token;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    PayPalProvider(@Value("${payment.paypal.client-id}") String clientId,
                   @Value("${payment.paypal.client-secret}") String clientSecret,
                   @Value("${payment.paypal.base-url:https://api-m.sandbox.paypal.com}") String baseUrl,
                   @Value("${payment.public-base-url}") String callbackBase,
                   @Value("${payment.paypal.brand-name:CongThanh Store}") String brandName,
                   RestClient.Builder builder) {
        this.http = builder.baseUrl(baseUrl).build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.callbackBase = callbackBase;
        this.brandName = brandName;
    }

    @Override
    public String name() {
        return "paypal";
    }

    @Override
    public Set<PaymentMethod> methods() {
        return Set.of(PaymentMethod.PAYPAL);
    }

    @Override
    public ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp) {
        String back = callbackBase + "/api/ecommerce/payment/paypal";
        Map<String, Object> source = Map.of("paypal", Map.of("experience_context", Map.of(
                "brand_name", brandName,
                "return_url", back + "/return?orderId=" + payment.orderId(),
                "cancel_url", back + "/cancel?orderId=" + payment.orderId(),
                "user_action", "PAY_NOW")));
        Map<String, Object> body = Map.of(
                "intent", "AUTHORIZE",
                "purchase_units", List.of(Map.of(
                        "custom_id", payment.orderId(),
                        "amount", Map.of("currency_code", payment.amount().currency(),
                                "value", value(payment.amount().amount(), payment.amount().currency())))),
                "payment_source", source);
        JsonNode order = post("/v2/checkout/orders", body, "create-" + payment.orderId());
        String approve = null;
        for (JsonNode link : order.path("links")) {
            if ("payer-action".equals(link.path("rel").asString()) || "approve".equals(link.path("rel").asString())) {
                approve = link.path("href").asString();
            }
        }
        return new ProviderResult.ActionRequired(order.path("id").asString(), approve);
    }

    /** Khách quay về return_url: authorize order trên PayPal rồi báo kết quả. */
    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        String orderId = params.get("orderId");
        String paypalOrder = params.get("token");
        if (paypalOrder == null || orderId == null) {
            throw PaymentException.invalid("missing PayPal token");
        }
        if ("cancel".equals(params.get("outcome"))) {
            return new CallbackResult(orderId, paypalOrder, false, "PAYPAL_CANCELLED", null);
        }
        JsonNode res = post("/v2/checkout/orders/" + paypalOrder + "/authorize", Map.of(), "authorize-" + orderId);
        JsonNode unit = res.path("purchase_units").path(0);
        if (!orderId.equals(unit.path("custom_id").asString(orderId))) {
            throw PaymentException.invalid("PayPal order does not belong to order " + orderId);
        }
        JsonNode auth = unit.path("payments").path("authorizations").path(0);
        boolean ok = "COMPLETED".equals(res.path("status").asString())
                && List.of("CREATED", "CAPTURED", "PENDING").contains(auth.path("status").asString());
        return new CallbackResult(orderId, paypalOrder, ok, ok ? null : "PAYPAL_" + auth.path("status").asString(), null);
    }

    @Override
    public boolean capture(Payment payment) {
        String authId = authorizationId(payment);
        post("/v2/payments/authorizations/" + authId + "/capture", Map.of("final_capture", true), "capture-" + payment.orderId());
        return true;
    }

    @Override
    public void voidAuthorization(Payment payment) {
        post("/v2/payments/authorizations/" + authorizationId(payment) + "/void", Map.of(), "void-" + payment.orderId());
    }

    @Override
    public void refund(Payment payment) {
        JsonNode capture = paymentsOf(payment).path("captures").path(0);
        if (capture.isMissingNode()) {
            throw PaymentException.conflict("PayPal order " + payment.providerRef() + " has no capture");
        }
        post("/v2/payments/captures/" + capture.path("id").asString() + "/refund", Map.of(), "refund-" + payment.orderId());
    }

    private String authorizationId(Payment payment) {
        JsonNode auth = paymentsOf(payment).path("authorizations").path(0);
        if (auth.isMissingNode()) {
            throw PaymentException.conflict("PayPal order " + payment.providerRef() + " has no authorization");
        }
        return auth.path("id").asString();
    }

    private JsonNode paymentsOf(Payment payment) {
        try {
            JsonNode order = http.get().uri("/v2/checkout/orders/{id}", payment.providerRef())
                    .header("Authorization", "Bearer " + accessToken()).retrieve().body(JsonNode.class);
            return order.path("purchase_units").path(0).path("payments");
        } catch (RestClientException e) {
            throw PaymentException.unavailable("paypal: " + e.getMessage());
        }
    }

    private JsonNode post(String path, Object body, String requestId) {
        try {
            return http.post().uri(path)
                    .header("Authorization", "Bearer " + accessToken())
                    .header("PayPal-Request-Id", requestId)
                    .header("Prefer", "return=representation")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw PaymentException.unavailable("paypal " + path + ": " + e.getMessage());
        }
    }

    private synchronized String accessToken() {
        if (token != null && Instant.now().isBefore(tokenExpiry)) {
            return token;
        }
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        JsonNode res = http.post().uri("/v1/oauth2/token")
                .header("Authorization", "Basic " + Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes()))
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form).retrieve().body(JsonNode.class);
        token = res.path("access_token").asString();
        tokenExpiry = Instant.now().plusSeconds(Math.max(60, res.path("expires_in").asLong(300) - 60));
        return token;
    }

    private static String value(BigDecimal amount, String currency) {
        int digits = Math.max(0, java.util.Currency.getInstance(currency).getDefaultFractionDigits());
        return amount.setScale(digits, java.math.RoundingMode.HALF_UP).toPlainString();
    }
}
