package com.congthanh.returnsservice.domain.event;

import com.congthanh.returnsservice.domain.model.ReturnLine;

import java.math.BigDecimal;
import java.util.List;

public sealed interface ReturnEvent {

    String returnId();

    record Requested(String returnId, String orderId, String customerId, String rmaCode, List<ReturnLine> lines)
            implements ReturnEvent {
    }

    record Approved(String returnId, String orderId, String customerId, String rmaCode, String approvedBy)
            implements ReturnEvent {
    }

    record Rejected(String returnId, String orderId, String customerId, String reason) implements ReturnEvent {
    }

    record Received(String returnId, String orderId, List<ReturnLine> lines) implements ReturnEvent {
    }

    record RefundIssued(String returnId, String orderId, String customerId, BigDecimal amount,
                        BigDecimal restockingFee, String currency, String paymentId,
                        com.congthanh.returnsservice.domain.model.RefundMethod method) implements ReturnEvent {
    }

    record Closed(String returnId, String orderId, String outcome) implements ReturnEvent {
    }
}
