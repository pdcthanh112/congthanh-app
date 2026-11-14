package com.congthanh.disputeservice.adapter.in.web;

import com.congthanh.disputeservice.application.port.in.DisputeIntake;
import com.congthanh.disputeservice.domain.exception.DisputeException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.Dispute;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.Locale;
import java.util.Set;

/**
 * Webhook Stripe riêng cho tranh chấp (một endpoint trong Stripe Dashboard chỉ đăng ký charge.dispute.*).
 * Không cần token người dùng: tin cậy dựa trên chữ ký Stripe-Signature với secret riêng.
 */
@RestController
@RequestMapping("/api/ecommerce/disputes/webhooks")
class StripeWebhookController {

    private static final Set<String> TYPES = Set.of("charge.dispute.created", "charge.dispute.updated",
            "charge.dispute.closed", "charge.dispute.funds_withdrawn", "charge.dispute.funds_reinstated");

    private final DisputeIntake intake;
    private final String secret;

    StripeWebhookController(DisputeIntake intake, @Value("${disputes.stripe.webhook-secret:}") String secret) {
        this.intake = intake;
        this.secret = secret;
    }

    @PostMapping("/stripe")
    ResponseEntity<Void> stripe(@RequestBody String payload, @RequestHeader("Stripe-Signature") String signature) {
        if (secret == null || secret.isBlank()) {
            throw DisputeException.forbidden("stripe dispute webhook is not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, secret);
        } catch (SignatureVerificationException e) {
            throw DisputeException.invalid("bad Stripe signature");
        }
        if (!TYPES.contains(event.getType())) {
            return ResponseEntity.noContent().build();
        }
        Dispute d = (Dispute) event.getDataObjectDeserializer().getObject()
                .orElseThrow(() -> DisputeException.invalid("cannot read dispute object (API version mismatch)"));
        String currency = d.getCurrency().toUpperCase(Locale.ROOT);
        long feeMinor = d.getBalanceTransactions() == null ? 0 : d.getBalanceTransactions().stream()
                .mapToLong(BalanceTransaction::getFee).sum();
        Instant due = d.getEvidenceDetails() == null || d.getEvidenceDetails().getDueBy() == null ? null
                : Instant.ofEpochSecond(d.getEvidenceDetails().getDueBy());
        intake.receive(new DisputeIntake.ProviderDispute("stripe", d.getId(), d.getPaymentIntent(),
                major(d.getAmount(), currency), major(feeMinor, currency), currency, d.getReason(), due, d.getStatus()));
        return ResponseEntity.noContent().build();
    }

    static BigDecimal major(long minor, String currency) {
        return BigDecimal.valueOf(minor, Math.max(0, Currency.getInstance(currency).getDefaultFractionDigits()));
    }
}
