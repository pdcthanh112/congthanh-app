package com.congthanh.disputeservice.domain;

import com.congthanh.disputeservice.domain.exception.DisputeException;
import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeReason;
import com.congthanh.disputeservice.domain.model.DisputeStatus;
import com.congthanh.disputeservice.domain.model.Evidence;
import com.congthanh.disputeservice.domain.model.EvidenceAssembler;
import com.congthanh.disputeservice.domain.model.OrderFacts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DisputeTest {

    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
    private static final Instant DUE = NOW.plus(Duration.ofDays(7));

    private static Dispute dispute(DisputeReason reason) {
        Dispute d = Dispute.open("d-1", "stripe", "dp_1", "pay-1", "o-1", "c-1", new BigDecimal("80.00"),
                new BigDecimal("15.00"), "USD", reason, DUE, NOW);
        d.pullEvents();
        return d;
    }

    private static OrderFacts facts(Instant deliveredAt, String refunded) {
        return new OrderFacts("o-1", "c-1", "Jane Doe", "NY", "10001", List.of("p-1"), new BigDecimal("80.00"),
                "UPS", "1Z999", NOW.minus(Duration.ofDays(10)), deliveredAt, new BigDecimal(refunded),
                refunded.equals("0") ? null : NOW.minus(Duration.ofDays(2)));
    }

    @Test
    void reasonCodesAreNormalised() {
        assertThat(DisputeReason.fromProvider("product_not_received")).isEqualTo(DisputeReason.PRODUCT_NOT_RECEIVED);
        assertThat(DisputeReason.fromProvider("unrecognized")).isEqualTo(DisputeReason.FRAUDULENT);
        assertThat(DisputeReason.fromProvider("bank_cannot_process")).isEqualTo(DisputeReason.GENERAL);
    }

    @Test
    void deliveredOrderIsWorthFightingForNotReceived() {
        Evidence e = EvidenceAssembler.assemble(dispute(DisputeReason.PRODUCT_NOT_RECEIVED),
                facts(NOW.minus(Duration.ofDays(5)), "0"), "30 days");
        assertThat(e.recommendation()).isEqualTo(Evidence.Recommendation.FIGHT);
        assertThat(e.fields()).containsEntry("shipping_tracking_number", "1Z999").containsKey("uncategorized_text");
    }

    @Test
    void notReceivedWithoutDeliveryProofShouldBeAccepted() {
        Evidence e = EvidenceAssembler.assemble(dispute(DisputeReason.PRODUCT_NOT_RECEIVED), facts(null, "0"), "30 days");
        assertThat(e.recommendation()).isEqualTo(Evidence.Recommendation.ACCEPT);
    }

    @Test
    void fullRefundBeforeDisputeWinsWhateverTheReason() {
        Evidence e = EvidenceAssembler.assemble(dispute(DisputeReason.FRAUDULENT), facts(null, "80.00"), "30 days");
        assertThat(e.recommendation()).isEqualTo(Evidence.Recommendation.FIGHT);
        assertThat(e.fields()).containsKey("refund_refusal_explanation");
    }

    @Test
    void unknownOrderNeedsReview() {
        Evidence e = EvidenceAssembler.assemble(dispute(DisputeReason.GENERAL), null, "30 days");
        assertThat(e.recommendation()).isEqualTo(Evidence.Recommendation.REVIEW);
    }

    @Test
    void cannotSubmitWithoutEvidenceOrAfterDeadline() {
        Dispute d = dispute(DisputeReason.GENERAL);
        assertThatThrownBy(() -> d.submitted("u", NOW)).isInstanceOf(DisputeException.class);
        d.attachEvidence(EvidenceAssembler.assemble(d, null, "x"), NOW);
        assertThatThrownBy(() -> d.submitted("u", DUE.plusSeconds(1))).isInstanceOf(DisputeException.class);
        d.submitted("u", NOW);
        assertThat(d.status()).isEqualTo(DisputeStatus.SUBMITTED);
        assertThat(d.pullEvents()).singleElement().extracting(ev -> ev.getClass().getSimpleName()).isEqualTo("EvidenceSubmitted");
    }

    @Test
    void closingIsIdempotentAndFinal() {
        Dispute d = dispute(DisputeReason.GENERAL);
        d.closed(true, NOW);
        d.closed(true, NOW);
        assertThat(d.pullEvents()).hasSize(1);
        assertThatThrownBy(() -> d.closed(false, NOW)).isInstanceOf(DisputeException.class);
    }

    @Test
    void dueSoonOnlyForOpenDisputes() {
        Dispute d = dispute(DisputeReason.GENERAL);
        assertThat(d.dueSoon(NOW, Duration.ofDays(3))).isFalse();
        assertThat(d.dueSoon(NOW.plus(Duration.ofDays(5)), Duration.ofDays(3))).isTrue();
        d.accepted("u", "small amount", NOW);
        assertThat(d.dueSoon(NOW.plus(Duration.ofDays(5)), Duration.ofDays(3))).isFalse();
    }
}
