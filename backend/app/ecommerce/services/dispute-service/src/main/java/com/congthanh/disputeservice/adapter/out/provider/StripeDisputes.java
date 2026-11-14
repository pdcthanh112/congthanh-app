package com.congthanh.disputeservice.adapter.out.provider;

import com.congthanh.disputeservice.application.port.out.ProviderDisputes;
import com.congthanh.disputeservice.domain.exception.DisputeException;
import com.congthanh.disputeservice.domain.model.Dispute;
import com.stripe.exception.StripeException;
import com.stripe.net.RequestOptions;
import com.stripe.param.DisputeUpdateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Nộp bằng chứng / chấp nhận thua qua Stripe. Không có khoá Stripe (local) thì chỉ ghi log để luồng vẫn chạy.
 * Tranh chấp của cổng khác (PayPal) hiện xử lý tay trên trang của cổng; service chỉ ghi trạng thái.
 */
@Component
class StripeDisputes implements ProviderDisputes {

    private static final Logger log = LoggerFactory.getLogger(StripeDisputes.class);

    private final String apiKey;

    StripeDisputes(@Value("${disputes.stripe.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void submitEvidence(Dispute d, Map<String, String> fields) {
        if (!stripeLive(d)) {
            log.info("sandbox: would submit evidence {} for {} dispute {}", fields.keySet(), d.provider(), d.providerDisputeId());
            return;
        }
        DisputeUpdateParams.Evidence.Builder e = DisputeUpdateParams.Evidence.builder();
        fields.forEach((k, v) -> e.putExtraParam(k, v));
        try {
            com.stripe.model.Dispute.retrieve(d.providerDisputeId(), options("evidence-" + d.id()))
                    .update(DisputeUpdateParams.builder().setEvidence(e.build()).setSubmit(true).build(),
                            options("submit-" + d.id()));
        } catch (StripeException ex) {
            throw DisputeException.conflict("stripe rejected evidence: " + ex.getMessage());
        }
    }

    @Override
    public void accept(Dispute d) {
        if (!stripeLive(d)) {
            log.info("sandbox: would accept {} dispute {}", d.provider(), d.providerDisputeId());
            return;
        }
        try {
            com.stripe.model.Dispute.retrieve(d.providerDisputeId(), options(null)).close(options("close-" + d.id()));
        } catch (StripeException ex) {
            throw DisputeException.conflict("stripe rejected close: " + ex.getMessage());
        }
    }

    private boolean stripeLive(Dispute d) {
        return "stripe".equalsIgnoreCase(d.provider()) && apiKey != null && !apiKey.isBlank();
    }

    private RequestOptions options(String idempotencyKey) {
        var b = RequestOptions.builder().setApiKey(apiKey);
        if (idempotencyKey != null) {
            b.setIdempotencyKey(idempotencyKey);
        }
        return b.build();
    }
}
