package com.congthanh.returnsservice.adapter.in.workflow;

import com.congthanh.returnsservice.application.port.out.RefundWorkflows.RefundRequest;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * Hoàn tiền một yêu cầu trả: gọi payment-service (task queue {@code payment}) rồi báo kết quả về
 * returns-service (task queue {@code returns}). Workflow id {@code return-refund-<returnId>} nên khởi chạy
 * lại không tạo lần hoàn thứ hai; refundId gửi cổng cũng chính là returnId.
 */
@WorkflowInterface
public interface ReturnRefundWorkflow {

    String TYPE = "ReturnRefundWorkflow";

    static String workflowId(String returnId) {
        return "return-refund-" + returnId;
    }

    @WorkflowMethod(name = TYPE)
    void refund(RefundRequest request);
}
