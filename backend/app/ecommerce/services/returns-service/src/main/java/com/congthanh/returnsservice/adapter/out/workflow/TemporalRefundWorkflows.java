package com.congthanh.returnsservice.adapter.out.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.returnsservice.application.port.out.RefundWorkflows;
import io.temporal.api.enums.v1.WorkflowIdReusePolicy;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowOptions;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Khởi chạy workflow bằng tên kiểu (untyped) để adapter ra không phụ thuộc adapter vào. */
@Component
@RequiredArgsConstructor
class TemporalRefundWorkflows implements RefundWorkflows {

    static final String TYPE = "ReturnRefundWorkflow";

    private final WorkflowClient client;

    @Override
    public void start(RefundRequest request) {
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId("return-refund-" + request.returnId())
                .setTaskQueue(TaskQueues.RETURNS)
                // Cho phép chạy lại sau khi lần trước kết thúc bằng REFUND_FAILED (thử lại sau khi đã xử lý).
                .setWorkflowIdReusePolicy(WorkflowIdReusePolicy.WORKFLOW_ID_REUSE_POLICY_ALLOW_DUPLICATE)
                .build();
        try {
            client.newUntypedWorkflowStub(TYPE, options).start(request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            // đang chạy: không làm gì
        }
    }
}
