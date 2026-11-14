package com.congthanh.returnsservice.domain;

import com.congthanh.returnsservice.domain.event.ReturnEvent;
import com.congthanh.returnsservice.domain.exception.ReturnException;
import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.congthanh.returnsservice.domain.model.OrderSnapshot;
import com.congthanh.returnsservice.domain.model.ReturnLine;
import com.congthanh.returnsservice.domain.model.ReturnPolicy;
import com.congthanh.returnsservice.domain.model.ReturnReason;
import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnRequestTest {

    private static final Instant DELIVERED = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant NOW = DELIVERED.plus(Duration.ofDays(5));
    private static final ReturnPolicy POLICY = new ReturnPolicy(Duration.ofDays(30), new BigDecimal("10"), true);

    private static OrderSnapshot order(Instant deliveredAt) {
        return new OrderSnapshot("o-1", "c-1", "CONFIRMED", "pay-1", "USD", new BigDecimal("150.00"), List.of(
                new OrderSnapshot.Line("p-shoe", 2, new BigDecimal("50.00")),
                new OrderSnapshot.Line("p-hat", 1, new BigDecimal("40.00"))), deliveredAt);
    }

    private static ReturnRequest open(List<ReturnLine> lines, Map<String, Integer> already) {
        return ReturnRequest.open("r-1", "RMA-ABCD2345", order(DELIVERED), "c-1", lines, POLICY, already, NOW);
    }

    @Test
    void validRequestIsPricedFromOrderAndAutoApproved() {
        ReturnRequest r = open(List.of(ReturnLine.requested("p-shoe", 1, ReturnReason.DAMAGED)), Map.of());

        assertThat(r.status()).isEqualTo(ReturnStatus.APPROVED);
        assertThat(r.lines().getFirst().unitPrice()).isEqualByComparingTo("50.00");
        assertThat(r.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Requested", "Approved");
    }

    @Test
    void cannotReturnAfterWindowOrBeforeDelivery() {
        assertThatThrownBy(() -> ReturnRequest.open("r", "RMA", order(DELIVERED), "c-1",
                List.of(ReturnLine.requested("p-hat", 1, ReturnReason.OTHER)), POLICY, Map.of(),
                DELIVERED.plus(Duration.ofDays(31))))
                .isInstanceOf(ReturnException.class).extracting("code").isEqualTo("RETURN_WINDOW_CLOSED");
        assertThatThrownBy(() -> ReturnRequest.open("r", "RMA", order(null), "c-1",
                List.of(ReturnLine.requested("p-hat", 1, ReturnReason.OTHER)), POLICY, Map.of(), NOW))
                .isInstanceOf(ReturnException.class).extracting("code").isEqualTo("NOT_DELIVERED");
    }

    @Test
    void cannotReturnMoreThanBoughtAcrossRequests() {
        assertThatThrownBy(() -> open(List.of(ReturnLine.requested("p-shoe", 2, ReturnReason.OTHER)),
                Map.of("p-shoe", 1)))
                .isInstanceOf(ReturnException.class).extracting("code").isEqualTo("QUANTITY_EXCEEDED");
        assertThatThrownBy(() -> open(List.of(ReturnLine.requested("p-other", 1, ReturnReason.OTHER)), Map.of()))
                .isInstanceOf(ReturnException.class).extracting("code").isEqualTo("NOT_IN_ORDER");
    }

    @Test
    void someoneElsesOrderIsForbidden() {
        assertThatThrownBy(() -> ReturnRequest.open("r", "RMA", order(DELIVERED), "c-2",
                List.of(ReturnLine.requested("p-hat", 1, ReturnReason.OTHER)), POLICY, Map.of(), NOW))
                .isInstanceOf(ReturnException.class).extracting("kind").isEqualTo(ReturnException.Kind.FORBIDDEN);
    }

    @Test
    void inspectionComputesRefundWithRestockingFeeOnlyWhenNotSellerFault() {
        ReturnRequest r = open(List.of(
                ReturnLine.requested("p-shoe", 2, ReturnReason.NO_LONGER_NEEDED),
                ReturnLine.requested("p-hat", 1, ReturnReason.DAMAGED)), Map.of());
        r.pullEvents();

        boolean refund = r.receive(Map.of("p-shoe", ItemCondition.PASSED, "p-hat", ItemCondition.DAMAGED), POLICY,
                new BigDecimal("150.00"), NOW);

        // giày 100 − 10% phí = 90; mũ hỏng do người bán = 40 đủ
        assertThat(refund).isTrue();
        assertThat(r.status()).isEqualTo(ReturnStatus.REFUND_PENDING);
        assertThat(r.refundAmount()).isEqualByComparingTo("130.00");
        assertThat(r.restockingFee()).isEqualByComparingTo("10.00");
    }

    @Test
    void refundNeverExceedsWhatIsLeftOnTheOrder() {
        ReturnRequest r = open(List.of(ReturnLine.requested("p-shoe", 2, ReturnReason.DAMAGED)), Map.of());
        r.receive(Map.of("p-shoe", ItemCondition.PASSED), POLICY, new BigDecimal("60.00"), NOW);
        assertThat(r.refundAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    void failedInspectionClosesTheReturnWithoutRefund() {
        ReturnRequest r = open(List.of(ReturnLine.requested("p-hat", 1, ReturnReason.NO_LONGER_NEEDED)), Map.of());
        r.pullEvents();

        assertThat(r.receive(Map.of("p-hat", ItemCondition.NOT_OURS), POLICY, new BigDecimal("150"), NOW)).isFalse();
        assertThat(r.status()).isEqualTo(ReturnStatus.REJECTED);
        assertThat(r.pullEvents()).extracting(e -> e.getClass().getSimpleName())
                .containsExactly("Received", "Rejected", "Closed");
    }

    @Test
    void refundedIsIdempotentAndEmitsRefundIssued() {
        ReturnRequest r = open(List.of(ReturnLine.requested("p-hat", 1, ReturnReason.WRONG_ITEM)), Map.of());
        r.receive(Map.of("p-hat", ItemCondition.PASSED), POLICY, new BigDecimal("150"), NOW);
        r.pullEvents();

        r.refunded("pay-1", NOW);
        r.refunded("pay-1", NOW);

        assertThat(r.status()).isEqualTo(ReturnStatus.REFUNDED);
        assertThat(r.pullEvents()).hasSize(2).first().isEqualTo(new ReturnEvent.RefundIssued("r-1", "o-1", "c-1",
                new BigDecimal("40.00"), new BigDecimal("0.00"), "USD", "pay-1",
                com.congthanh.returnsservice.domain.model.RefundMethod.ORIGINAL_PAYMENT));
    }

    @Test
    void customerCanCancelOnlyBeforeItemsArrive() {
        ReturnRequest r = open(List.of(ReturnLine.requested("p-hat", 1, ReturnReason.OTHER)), Map.of());
        assertThatThrownBy(() -> r.cancel("c-2", NOW)).isInstanceOf(ReturnException.class);
        r.cancel("c-1", NOW);
        assertThat(r.status()).isEqualTo(ReturnStatus.CANCELLED);

        ReturnRequest received = open(List.of(ReturnLine.requested("p-hat", 1, ReturnReason.OTHER)), Map.of());
        received.receive(Map.of("p-hat", ItemCondition.PASSED), POLICY, new BigDecimal("150"), NOW);
        assertThatThrownBy(() -> received.cancel("c-1", NOW)).isInstanceOf(ReturnException.class);
    }
}
