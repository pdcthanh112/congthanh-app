package com.congthanh.orderservice.adapter.out.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.orderservice.application.port.out.CheckoutOrchestrator;
import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.Order;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Hiện thực port CheckoutOrchestrator bằng Temporal client, dùng stub không kiểu (untyped) để
 * adapter ra không phụ thuộc lớp workflow của adapter vào; hợp đồng là tên trong CheckoutContract.
 */
@Component
class TemporalCheckoutOrchestrator implements CheckoutOrchestrator {

    private final WorkflowClient client;
    private final Duration paymentTimeout;
    private final Duration executionTimeout;

    TemporalCheckoutOrchestrator(WorkflowClient client,
                                 @Value("${order.checkout.payment-timeout:PT15M}") Duration paymentTimeout,
                                 @Value("${order.checkout.execution-timeout:P7D}") Duration executionTimeout) {
        this.client = client;
        this.paymentTimeout = paymentTimeout;
        this.executionTimeout = executionTimeout;
    }

    @Override
    public void start(Order order) {
        WorkflowStub stub = client.newUntypedWorkflowStub(CheckoutContract.WORKFLOW_TYPE, WorkflowOptions.newBuilder()
                .setWorkflowId(CheckoutContract.workflowId(order.id()))
                // Workflow do checkout-service chạy; order-service chỉ start (sau khi lưu đơn) và chạy activity.
                .setTaskQueue(TaskQueues.CHECKOUT_WORKFLOW)
                // Start lại cùng đơn: nếu đang chạy hoặc đã xong thì bỏ qua; chỉ chạy lại nếu lần trước lỗi hệ thống.
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE_FAILED_ONLY)
                .setWorkflowExecutionTimeout(executionTimeout)
                .build());
        var c = order.checkout();
        var input = new CheckoutInputPayload(order.id(), order.customerId(), order.total().amount(),
                order.total().currency(), c.paymentMethod().name(), c.paymentToken(), c.returnUrl(), c.clientIp(),
                order.hasPromotion(), paymentTimeout.toSeconds());
        try {
            stub.start(input);
        } catch (WorkflowExecutionAlreadyStarted ignored) {
            // idempotent
        } catch (StatusRuntimeException e) {
            throw OrderException.unavailable("temporal: " + e.getStatus());
        }
    }

    @Override
    public Optional<Progress> progress(String orderId) {
        try {
            WorkflowStub stub = client.newUntypedWorkflowStub(CheckoutContract.workflowId(orderId));
            StatePayload s = stub.query(CheckoutContract.QUERY_STATUS, StatePayload.class);
            return Optional.of(new Progress(s.step(), s.paymentRedirectUrl(), s.failureReason()));
        } catch (WorkflowNotFoundException e) {
            return Optional.empty();
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw OrderException.unavailable("temporal: " + e.getStatus());
        } catch (RuntimeException e) {
            // Query lỗi (worker chưa sẵn sàng) không được làm hỏng API đọc: rơi về trạng thái trong DB.
            return Optional.empty();
        }
    }

    @Override
    public void requestCancel(String orderId, String reason) {
        try {
            client.newUntypedWorkflowStub(CheckoutContract.workflowId(orderId)).signal(CheckoutContract.SIGNAL_CANCEL, reason);
        } catch (WorkflowNotFoundException e) {
            throw OrderException.conflict("checkout for order is no longer running");
        }
    }

    /** Cùng shape JSON với CheckoutInput của workflow (Temporal dùng Jackson converter). */
    record CheckoutInputPayload(String orderId, String customerId, java.math.BigDecimal amount, String currency,
                                String paymentMethod, String paymentToken, String returnUrl, String clientIp,
                                boolean hasPromotion, long paymentTimeoutSeconds) {
    }

    record StatePayload(String step, String paymentRedirectUrl, String failureReason) {
    }
}
