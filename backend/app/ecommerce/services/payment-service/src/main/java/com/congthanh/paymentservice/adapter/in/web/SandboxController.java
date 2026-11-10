package com.congthanh.paymentservice.adapter.in.web;

import com.congthanh.paymentservice.application.port.in.PaymentUseCases;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

import java.util.Map;

/** Trang "cổng thanh toán" giả cho local/e2e. Chỉ tồn tại khi payment.sandbox.enabled=true. */
@RestController
@RequestMapping("/api/ecommerce/payment/sandbox")
@ConditionalOnBooleanProperty("payment.sandbox.enabled")
public class SandboxController {

    private final PaymentUseCases payments;
    private final SandboxSigner signer;

    SandboxController(PaymentUseCases payments, SandboxSigner signer) {
        this.payments = payments;
        this.signer = signer;
    }

    @GetMapping(value = "/pay", produces = MediaType.TEXT_HTML_VALUE)
    String page(@RequestParam String ref, @RequestParam String method, @RequestParam String sig) {
        if (!signer.valid(ref, "page", sig)) {
            return "<p>invalid link</p>";
        }
        String r = HtmlUtils.htmlEscape(ref);
        String m = HtmlUtils.htmlEscape(method);
        String base = "callback?method=" + m + "&ref=" + r;
        return "<!doctype html><meta charset=utf-8><title>Sandbox payment</title>"
                + "<h1>Sandbox " + m + "</h1><p>Reference " + r + "</p>"
                + "<p><a href=\"" + base + "&result=success&sig=" + signer.sign(ref, "success") + "\">Pay</a> · "
                + "<a href=\"" + base + "&result=fail&sig=" + signer.sign(ref, "fail") + "\">Fail</a></p>";
    }

    @GetMapping(value = "/callback", produces = MediaType.TEXT_HTML_VALUE)
    String callback(@RequestParam String method, @RequestParam Map<String, String> params) {
        var out = payments.handleCallback(PaymentMethod.valueOf(method), params);
        return "<!doctype html><meta charset=utf-8><p>Order " + HtmlUtils.htmlEscape(out.orderId()) + ": "
                + (out.success() ? "paid" : "failed") + "</p>";
    }

    /** Cổng phụ để controller không phụ thuộc trực tiếp vào adapter provider. */
    public interface SandboxSigner {
        String sign(String ref, String purpose);

        default boolean valid(String ref, String purpose, String sig) {
            return sign(ref, purpose).equalsIgnoreCase(sig);
        }
    }
}
