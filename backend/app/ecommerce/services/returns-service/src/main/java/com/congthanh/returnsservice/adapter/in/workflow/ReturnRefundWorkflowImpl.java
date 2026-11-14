package com.congthanh.returnsservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.ecommerce.workflow.payment.PartialRefundRequest;
import com.congthanh.ecommerce.workflow.payment.PartialRefundResult;
import com.congthanh.ecommerce.workflow.payment.PaymentActivities;
import com.congthanh.ecommerce.workflow.payment.PaymentFailures;
import com.congthanh.ecommerce.workflow.storedvalue.StoredValueActivities;
import com.congthanh.returnsservice.application.port.out.RefundWorkflows.RefundRequest;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;

import java.time.Duration;

public class ReturnRefundWorkflowImpl implements ReturnRefundWorkflow {

    /** Cổng tạm lỗi thì thử lại tối đa một ngày; lỗi nghiệp vụ (vượt số còn hoàn) thì dừng ngay. */
    private static final RetryOptions PAYMENT_RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(2))
            .setBackoffCoefficient(2)
            .setMaximumInterval(Duration.ofMinutes(10))
            .setDoNotRetry(PaymentFailures.REFUND_REJECTED)
            .build();

    private final PaymentActivities payments = Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.PAYMENT)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setScheduleToCloseTimeout(Duration.ofDays(1))
                    .setRetryOptions(PAYMENT_RETRY)
                    .build());

    private final ReturnActivities returns = Workflow.newActivityStub(ReturnActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.RETURNS)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1))
                            .setMaximumInterval(Duration.ofMinutes(1)).build())
                    .build());

    private final StoredValueActivities storedValue = Workflow.newActivityStub(StoredValueActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.STORED_VALUE)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setScheduleToCloseTimeout(Duration.ofDays(1))
                    .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(2))
                            .setMaximumInterval(Duration.ofMinutes(10))
                            .setDoNotRetry(StoredValueActivities.REJECTED).build())
                    .build());

    @Override
    public void refund(RefundRequest r) {
        String paymentId = null;
        try {
            if ("STORE_CREDIT".equals(r.method())) {
                storedValue.credit(new StoredValueActivities.CreditRequest(r.customerId(), r.amount(), r.currency(),
                        "RETURN_REFUND", r.returnId()));
            } else {
                PartialRefundResult result = payments.refundPartial(new PartialRefundRequest(r.orderId(), r.returnId(),
                        r.amount(), r.currency(), r.reason()));
                paymentId = result.paymentId();
            }
        } catch (ActivityFailure f) {
            String reason = f.getCause() instanceof ApplicationFailure af ? af.getOriginalMessage() : f.getMessage();
            returns.markRefundFailed(r.returnId(), reason);
            return;
        }
        returns.markRefunded(r.returnId(), paymentId);
    }
}
