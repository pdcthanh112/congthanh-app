package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.Payment;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Cổng giả cho local/dev/e2e (payment.sandbox.enabled=true), KHÔNG bao giờ bật ở prod.
 * <ul>
 *   <li>token {@code tok_decline}: bị từ chối (để thử nhánh bù trừ của saga)</li>
 *   <li>VN_PAY, PAYPAL, MOMO hoặc token {@code tok_redirect}: phải sang trang sandbox để bấm Trả/Huỷ</li>
 *   <li>còn lại: authorize ngay</li>
 * </ul>
 * Link trang sandbox có chữ ký HMAC nên không ai tự "trả hộ" bằng cách đoán URL.
 */
@Component
@ConditionalOnBooleanProperty("payment.sandbox.enabled")
public class SandboxProvider implements PaymentProvider {

    private final String secret;
    private final String publicBaseUrl;

    SandboxProvider(@Value("${payment.sandbox.secret:sandbox-secret}") String secret,
                    @Value("${payment.public-base-url}") String publicBaseUrl) {
        this.secret = secret;
        this.publicBaseUrl = publicBaseUrl;
    }

    @Override
    public String name() {
        return "sandbox";
    }

    @Override
    public Set<PaymentMethod> methods() {
        return EnumSet.complementOf(EnumSet.of(PaymentMethod.CASH_ON_DELIVERY));
    }

    @Override
    public ProviderResult authorize(Payment payment, String paymentToken, String returnUrl, String clientIp) {
        String ref = "sbx_" + payment.id().replace("-", "");
        if ("tok_decline".equals(paymentToken)) {
            return new ProviderResult.Declined("card_declined (sandbox)");
        }
        boolean redirect = "tok_redirect".equals(paymentToken)
                || EnumSet.of(PaymentMethod.VN_PAY, PaymentMethod.PAYPAL, PaymentMethod.MOMO).contains(payment.method());
        if (redirect) {
            return new ProviderResult.ActionRequired(ref, publicBaseUrl + "/api/ecommerce/payment/sandbox/pay?ref=" + ref
                    + "&method=" + payment.method() + "&sig=" + sign(ref, "page"));
        }
        return new ProviderResult.Authorized(ref);
    }

    @Override
    public CallbackResult verifyCallback(Map<String, String> params) {
        String ref = params.get("ref");
        String result = params.get("result");
        if (ref == null || result == null || !Hmac.equalsConstantTime(sign(ref, result), params.get("sig"))) {
            throw PaymentException.invalid("bad sandbox signature");
        }
        boolean ok = "success".equals(result);
        return new CallbackResult(null, ref, ok, ok ? null : "SANDBOX_DECLINED", null);
    }

    public String sign(String ref, String purpose) {
        return Hmac.hex("HmacSHA256", secret, ref + "|" + purpose);
    }

    @Override
    public boolean capture(Payment payment) {
        return true;
    }

    @Override
    public void voidAuthorization(Payment payment) {
    }

    @Override
    public void refund(Payment payment) {
    }
}
