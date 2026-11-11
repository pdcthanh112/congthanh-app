package com.congthanh.checkoutservice.adapter.out.workflow;

import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows;
import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.ecommerce.workflow.checkout.CheckoutState;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowNotFoundException;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class TemporalCheckoutWorkflows implements CheckoutWorkflows {

    private final WorkflowClient client;

    TemporalCheckoutWorkflows(WorkflowClient client) {
        this.client = client;
    }

    @Override
    public Optional<Progress> progress(String orderId) {
        try {
            CheckoutState s = client.newUntypedWorkflowStub(CheckoutContract.workflowId(orderId))
                    .query(CheckoutContract.QUERY_STATUS, CheckoutState.class);
            return Optional.of(new Progress(s.step().name(), s.paymentRedirectUrl(), s.failureReason()));
        } catch (WorkflowNotFoundException e) {
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            return Optional.empty();
        } catch (RuntimeException e) {
            // Query lỗi (worker đang deploy) không làm hỏng API đọc phiên.
            return Optional.empty();
        }
    }

    @Override
    public void requestCancel(String orderId, String reason) {
        try {
            client.newUntypedWorkflowStub(CheckoutContract.workflowId(orderId)).signal(CheckoutContract.SIGNAL_CANCEL, reason);
        } catch (WorkflowNotFoundException e) {
            throw CheckoutException.conflict("CHECKOUT_FINISHED", "checkout for this order is no longer running");
        }
    }
}
