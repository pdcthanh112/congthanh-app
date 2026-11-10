package com.congthanh.orderservice.adapter.in.workflow;

import com.congthanh.orderservice.application.port.in.CheckoutStepsUseCase;
import com.congthanh.orderservice.domain.exception.OrderException;
import io.temporal.failure.ApplicationFailure;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * Lỗi nghiệp vụ (REJECTED, INVALID, NOT_FOUND, CONFLICT) → ApplicationFailure không retry, workflow
 * chuyển sang bù trừ. Lỗi hạ tầng (UNAVAILABLE, exception khác) → để Temporal retry theo RetryOptions.
 */
@Component
public class CheckoutActivitiesImpl implements CheckoutActivities {

    public static final String REJECTED = "CheckoutRejected";

    private final CheckoutStepsUseCase steps;

    public CheckoutActivitiesImpl(CheckoutStepsUseCase steps) {
        this.steps = steps;
    }

    @Override
    public void redeemPromotion(String orderId) {
        run(() -> { steps.redeemPromotion(orderId); return null; });
    }

    @Override
    public void cancelPromotionRedemption(String orderId, String reason) {
        run(() -> { steps.cancelPromotionRedemption(orderId, reason); return null; });
    }

    @Override
    public String reserveInventory(String orderId) {
        return run(() -> steps.reserveInventory(orderId));
    }

    @Override
    public void releaseInventory(String orderId, String reason) {
        run(() -> { steps.releaseInventory(orderId, reason); return null; });
    }

    @Override
    public void commitInventory(String orderId) {
        run(() -> { steps.commitInventory(orderId); return null; });
    }

    @Override
    public void confirmOrder(String orderId, String paymentId) {
        run(() -> { steps.confirm(orderId, paymentId); return null; });
    }

    @Override
    public void cancelOrder(String orderId, String reason) {
        run(() -> { steps.cancel(orderId, reason); return null; });
    }

    private static <T> T run(Supplier<T> step) {
        try {
            return step.get();
        } catch (OrderException e) {
            if (e.kind() == OrderException.Kind.UNAVAILABLE) {
                throw e;
            }
            throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), REJECTED, e.code());
        }
    }
}
