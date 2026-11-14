package com.congthanh.returnsservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.ecommerce.workflow.payment.AuthorizePaymentRequest;
import com.congthanh.ecommerce.workflow.payment.PartialRefundRequest;
import com.congthanh.ecommerce.workflow.payment.PartialRefundResult;
import com.congthanh.ecommerce.workflow.payment.PaymentActivities;
import com.congthanh.ecommerce.workflow.payment.PaymentAuthorization;
import com.congthanh.ecommerce.workflow.payment.PaymentFailures;
import com.congthanh.ecommerce.workflow.payment.RefundPaymentRequest;
import com.congthanh.ecommerce.workflow.storedvalue.StoredValueActivities;
import com.congthanh.returnsservice.application.port.out.RefundWorkflows.RefundRequest;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ReturnRefundWorkflowTest {

    private TestWorkflowEnvironment env;
    private final FakePayments payments = new FakePayments();
    private final FakeReturns returns = new FakeReturns();
    private final FakeCredits credits = new FakeCredits();

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        Worker w = env.newWorker(TaskQueues.RETURNS);
        w.registerWorkflowImplementationTypes(ReturnRefundWorkflowImpl.class);
        w.registerActivitiesImplementations(returns);
        env.newWorker(TaskQueues.PAYMENT).registerActivitiesImplementations(payments);
        env.newWorker(TaskQueues.STORED_VALUE).registerActivitiesImplementations(credits);
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private void run(String returnId) {
        run(returnId, "ORIGINAL_PAYMENT");
    }

    private void run(String returnId, String method) {
        ReturnRefundWorkflow wf = env.getWorkflowClient().newWorkflowStub(ReturnRefundWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue(TaskQueues.RETURNS)
                        .setWorkflowId(ReturnRefundWorkflow.workflowId(returnId)).build());
        wf.refund(new RefundRequest(returnId, "o-1", "c-1", new BigDecimal("40.00"), "USD", "return RMA-1", method));
    }

    @Test
    void refundsThroughPaymentWithReturnIdAsRefundId() {
        run("r-1");

        assertThat(payments.requests).singleElement()
                .satisfies(r -> assertThat(r.refundId()).isEqualTo("r-1"));
        assertThat(returns.refunded.get()).isEqualTo("r-1/pay-1");
        assertThat(returns.failed.get()).isNull();
    }

    @Test
    void rejectedRefundIsReportedNotRetried() {
        payments.reject = true;
        run("r-2");

        assertThat(payments.requests).hasSize(1);
        assertThat(returns.failed.get()).startsWith("r-2/").contains("exceeds");
        assertThat(returns.refunded.get()).isNull();
    }

    @Test
    void storeCreditRefundGoesToStoredValueNotPayment() {
        run("r-3", "STORE_CREDIT");

        assertThat(payments.requests).isEmpty();
        assertThat(credits.requests).singleElement()
                .satisfies(c -> assertThat(c.reference()).isEqualTo("r-3"))
                .satisfies(c -> assertThat(c.reason()).isEqualTo("RETURN_REFUND"));
        assertThat(returns.refunded.get()).isEqualTo("r-3/null");
    }

    static class FakeCredits implements StoredValueActivities {
        final List<CreditRequest> requests = new ArrayList<>();

        @Override
        public Result credit(CreditRequest request) {
            requests.add(request);
            return new Result("acc-1", request.amount());
        }

        @Override
        public Result hold(HoldRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void capture(String holdId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void release(String holdId, String reason) {
            throw new UnsupportedOperationException();
        }
    }

    static class FakePayments implements PaymentActivities {
        final List<PartialRefundRequest> requests = new ArrayList<>();
        volatile boolean reject;

        @Override
        public PaymentAuthorization authorize(AuthorizePaymentRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void capture(String orderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void refund(RefundPaymentRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PartialRefundResult refundPartial(PartialRefundRequest request) {
            requests.add(request);
            if (reject) {
                throw ApplicationFailure.newNonRetryableFailure("refund exceeds refundable", PaymentFailures.REFUND_REJECTED);
            }
            return new PartialRefundResult(request.refundId(), "pay-1", request.amount(), request.currency(), false);
        }
    }

    static class FakeReturns implements ReturnActivities {
        final AtomicReference<String> refunded = new AtomicReference<>();
        final AtomicReference<String> failed = new AtomicReference<>();

        @Override
        public void markRefunded(String returnId, String paymentId) {
            refunded.set(returnId + "/" + paymentId);
        }

        @Override
        public void markRefundFailed(String returnId, String reason) {
            failed.set(returnId + "/" + reason);
        }
    }
}
