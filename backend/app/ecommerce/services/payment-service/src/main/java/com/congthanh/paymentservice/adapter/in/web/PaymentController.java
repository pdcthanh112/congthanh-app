package com.congthanh.paymentservice.adapter.in.web;

import com.congthanh.paymentservice.application.port.in.PaymentUseCases;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Không còn endpoint "tạo thanh toán" công khai: thanh toán chỉ bắt đầu từ saga checkout.
 * HTTP ở đây là đọc trạng thái + callback của cổng (chữ ký được kiểm tra trong provider).
 */
@RestController
@RequestMapping("/api/ecommerce/payment")
@Tag(name = "Payments")
class PaymentController {

    private final PaymentUseCases payments;
    private final String frontendResultUrl;

    PaymentController(PaymentUseCases payments, @Value("${payment.frontend-result-url}") String frontendResultUrl) {
        this.payments = payments;
        this.frontendResultUrl = frontendResultUrl;
    }

    record PaymentView(String paymentId, String orderId, String status, String method, BigDecimal amount,
                       String currency, String redirectUrl, String failureReason, Instant updatedAt) {
        static PaymentView of(Payment p) {
            return new PaymentView(p.id(), p.orderId(), p.status().name(), p.method().name(), p.amount().amount(),
                    p.amount().currency(), p.redirectUrl(), p.failureReason(), p.updatedAt());
        }
    }

    @Operation(summary = "Payment of an order (owner or staff)")
    @GetMapping("/orders/{orderId}")
    ResponseEntity<PaymentView> byOrder(@PathVariable String orderId, Authentication auth) {
        Payment p = payments.getByOrder(orderId);
        boolean staff = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_SUPPORT"));
        if (!staff && !auth.getName().equals(p.customerId())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(PaymentView.of(p));
    }

    record InstallmentOptionView(String method, BigDecimal minAmount, BigDecimal maxAmount, String currency) {
    }

    /**
     * Đối tác trả góp hợp lệ cho số tiền (ADR 0016). Cố ý không trả số tiền mỗi kỳ hay APR: frontend dùng
     * Stripe Payment Method Messaging Element / PayPal Pay Later messages để hiển thị điều khoản.
     */
    @Operation(summary = "Pay-later partners available for an amount (public)")
    @GetMapping("/installment-options")
    List<InstallmentOptionView> installmentOptions(@RequestParam BigDecimal amount,
                                                   @RequestParam(defaultValue = "USD") String currency) {
        return payments.installmentOptions(amount, currency).stream()
                .map(o -> new InstallmentOptionView(o.method().name(), o.minAmount(), o.maxAmount(), o.currency()))
                .toList();
    }

    @GetMapping("/paypal/return")
    ResponseEntity<Void> paypalReturn(@RequestParam Map<String, String> params) {
        var out = payments.handleCallback(PaymentMethod.PAYPAL, params);
        return redirectToFrontend(out.orderId(), out.success());
    }

    @GetMapping("/paypal/cancel")
    ResponseEntity<Void> paypalCancel(@RequestParam Map<String, String> params) {
        Map<String, String> p = new HashMap<>(params);
        p.put("outcome", "cancel");
        var out = payments.handleCallback(PaymentMethod.PAYPAL, p);
        return redirectToFrontend(out.orderId(), false);
    }

    @PostMapping("/webhooks/stripe")
    ResponseEntity<Void> stripeWebhook(@RequestHeader("Stripe-Signature") String signature, @RequestBody String payload) {
        payments.handleCallback(PaymentMethod.CARD, Map.of("payload", payload, "signature", signature));
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> redirectToFrontend(String orderId, boolean success) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .header(HttpHeaders.LOCATION, URI.create(frontendResultUrl + "?orderId=" + orderId
                        + "&status=" + (success ? "success" : "failed")).toString())
                .build();
    }
}
