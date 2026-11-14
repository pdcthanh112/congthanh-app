package com.congthanh.returnsservice.adapter.in.workflow;

import com.congthanh.returnsservice.application.port.in.RefundOutcomes;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReturnActivitiesImpl implements ReturnActivities {

    private final RefundOutcomes outcomes;

    @Override
    public void markRefunded(String returnId, String paymentId) {
        outcomes.refunded(returnId, paymentId);
    }

    @Override
    public void markRefundFailed(String returnId, String reason) {
        outcomes.refundFailed(returnId, reason);
    }
}
