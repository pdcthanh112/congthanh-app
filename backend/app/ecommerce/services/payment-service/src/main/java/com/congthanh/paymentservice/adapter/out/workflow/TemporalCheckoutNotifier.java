package com.congthanh.paymentservice.adapter.out.workflow;

import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.ecommerce.workflow.checkout.PaymentCompleted;
import com.congthanh.paymentservice.application.port.out.CheckoutNotifier;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Signal tới workflow checkout của order-service; chỉ cần biết tên trong CheckoutContract. */
@Component
class TemporalCheckoutNotifier implements CheckoutNotifier {

    private static final Logger log = LoggerFactory.getLogger(TemporalCheckoutNotifier.class);
    private final WorkflowClient client;

    TemporalCheckoutNotifier(WorkflowClient client) {
        this.client = client;
    }

    @Override
    public void paymentCompleted(String orderId, String paymentId, boolean success, String reason) {
        try {
            client.newUntypedWorkflowStub(CheckoutContract.workflowId(orderId))
                    .signal(CheckoutContract.SIGNAL_PAYMENT_COMPLETED, new PaymentCompleted(paymentId, success, reason));
        } catch (WorkflowNotFoundException e) {
            // Saga đã kết thúc (ví dụ hết hạn chờ): service tự hoàn tiền nếu cần, không còn ai để báo.
            log.info("checkout for order {} is no longer running; payment callback recorded only", orderId);
        }
    }
}
