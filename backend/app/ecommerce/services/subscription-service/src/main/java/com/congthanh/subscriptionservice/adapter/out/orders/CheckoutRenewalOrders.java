package com.congthanh.subscriptionservice.adapter.out.orders;

import com.congthanh.subscriptionservice.application.port.out.RenewalOrders;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.Map;

/**
 * Đặt đơn gia hạn qua endpoint nội bộ của checkout-service ({@code POST /internal/checkout/subscription-orders}).
 * Lỗi 4xx (hết hàng, sản phẩm ngừng bán, địa chỉ không giao được) là từ chối nghiệp vụ → dunning; lỗi 5xx/mạng ném
 * ra để lần tick sau thử lại mà không tính là một lượt thất bại. Thẻ bị từ chối xảy ra sau, trong saga, và về qua
 * event OrderCancelled.
 */
@Component
@ConditionalOnProperty(name = "subscriptions.renewals.enabled", havingValue = "true")
class CheckoutRenewalOrders implements RenewalOrders {

    private final RestClient http;
    private final String promotionCode;

    CheckoutRenewalOrders(RestClient.Builder builder, @Value("${subscriptions.checkout-url}") String checkoutUrl,
                          @Value("${subscriptions.promotion-code:}") String promotionCode) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(2));
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.http = builder.baseUrl(checkoutUrl).requestFactory(factory).build();
        this.promotionCode = promotionCode;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public String place(Subscription s, String idempotencyKey) {
        Map<String, Object> body = Map.of(
                "idempotencyKey", idempotencyKey,
                "subscriptionId", s.id(),
                "customerId", s.customerId(),
                "lines", s.lines().stream().map(l -> Map.of("productId", l.productId(), "quantity", l.quantity())).toList(),
                "addressId", s.addressId() == null ? "" : s.addressId(),
                "paymentMethodRef", s.paymentMethodRef(),
                "promotionCode", promotionCode);
        try {
            Map<?, ?> res = http.post().uri("/internal/checkout/subscription-orders").body(body).retrieve().body(Map.class);
            Object orderId = res == null ? null : res.get("orderId");
            if (orderId == null) {
                throw new IllegalStateException("checkout returned no orderId");
            }
            return orderId.toString();
        } catch (RestClientResponseException e) {
            HttpStatusCode code = e.getStatusCode();
            if (code.is4xxClientError()) {
                String detail = e.getResponseBodyAsString().replaceAll("\\s+", " ");
                throw new Rejected("CHECKOUT_REJECTED " + code.value() + ": " + detail.substring(0, Math.min(300, detail.length())));
            }
            throw e;
        }
    }
}
