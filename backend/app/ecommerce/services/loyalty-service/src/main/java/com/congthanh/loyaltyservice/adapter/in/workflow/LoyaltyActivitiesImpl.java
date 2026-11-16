package com.congthanh.loyaltyservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.loyalty.LoyaltyActivities;
import com.congthanh.loyaltyservice.application.port.in.LoyaltyUseCases;
import com.congthanh.loyaltyservice.domain.exception.LoyaltyException;
import io.temporal.failure.ApplicationFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
public class LoyaltyActivitiesImpl implements LoyaltyActivities {

    private final LoyaltyUseCases loyalty;

    @Override
    public BigDecimal reserve(String redemptionId, String customerId, String orderId, long points, String currency) {
        try {
            return loyalty.reserve(redemptionId, customerId, orderId, points, currency);
        } catch (LoyaltyException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), REJECTED);
        }
    }

    @Override
    public void commit(String redemptionId) {
        try {
            loyalty.commit(redemptionId);
        } catch (LoyaltyException e) {
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), REJECTED);
        }
    }

    @Override
    public void release(String redemptionId) {
        loyalty.release(redemptionId);
    }
}
