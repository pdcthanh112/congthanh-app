package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.returns.v1.ReceivedLine;
import com.congthanh.ecommerce.returns.v1.RefundIssued;
import com.congthanh.ecommerce.returns.v1.ReturnApproved;
import com.congthanh.ecommerce.returns.v1.ReturnClosed;
import com.congthanh.ecommerce.returns.v1.ReturnLine;
import com.congthanh.ecommerce.returns.v1.ReturnReceived;
import com.congthanh.ecommerce.returns.v1.ReturnRejected;
import com.congthanh.ecommerce.returns.v1.ReturnRequested;
import com.congthanh.returnsservice.domain.event.ReturnEvent;
import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.google.protobuf.Message;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class ReturnEventMapper {

    private ReturnEventMapper() {
    }

    static Message toProto(ReturnEvent event) {
        return switch (event) {
            case ReturnEvent.Requested e -> ReturnRequested.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId()).setCustomerId(e.customerId())
                    .setRmaCode(e.rmaCode())
                    .addAllLines(e.lines().stream().map(l -> ReturnLine.newBuilder().setProductId(l.productId())
                            .setQuantity(l.quantity()).setReason(l.reason().name()).build()).toList())
                    .build();
            case ReturnEvent.Approved e -> ReturnApproved.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId()).setCustomerId(e.customerId())
                    .setRmaCode(e.rmaCode()).setApprovedBy(e.approvedBy()).build();
            case ReturnEvent.Rejected e -> ReturnRejected.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId()).setCustomerId(e.customerId())
                    .setReason(e.reason()).build();
            case ReturnEvent.Received e -> ReturnReceived.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId())
                    .addAllLines(e.lines().stream().map(l -> ReceivedLine.newBuilder().setProductId(l.productId())
                            .setQuantity(l.quantity())
                            .setCondition(l.condition() == null ? "" : l.condition().name())
                            .setRestock(l.condition() == ItemCondition.PASSED).build()).toList())
                    .build();
            case ReturnEvent.RefundIssued e -> RefundIssued.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId()).setCustomerId(e.customerId())
                    .setAmount(money(e.amount(), e.currency()))
                    .setRestockingFee(money(e.restockingFee() == null ? BigDecimal.ZERO : e.restockingFee(), e.currency()))
                    .setMethod(e.method().name()).setPaymentId(e.paymentId() == null ? "" : e.paymentId())
                    .build();
            case ReturnEvent.Closed e -> ReturnClosed.newBuilder()
                    .setReturnId(e.returnId()).setOrderId(e.orderId()).setOutcome(e.outcome()).build();
        };
    }

    static Money money(BigDecimal amount, String currency) {
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode(currency == null ? "USD" : currency).setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }
}
