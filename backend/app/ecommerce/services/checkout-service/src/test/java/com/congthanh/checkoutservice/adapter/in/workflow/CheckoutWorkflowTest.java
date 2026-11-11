package com.congthanh.checkoutservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.ecommerce.workflow.checkout.CheckoutActivities;
import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.ecommerce.workflow.checkout.CheckoutInput;
import com.congthanh.ecommerce.workflow.checkout.CheckoutResult;
import com.congthanh.ecommerce.workflow.checkout.CheckoutState;
import com.congthanh.ecommerce.workflow.checkout.PaymentCompleted;
import com.congthanh.ecommerce.workflow.payment.AuthorizePaymentRequest;
import com.congthanh.ecommerce.workflow.payment.PaymentActivities;
import com.congthanh.ecommerce.workflow.payment.PaymentAuthorization;
import com.congthanh.ecommerce.workflow.payment.PaymentFailures;
import com.congthanh.ecommerce.workflow.payment.RefundPaymentRequest;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Saga checkout chạy trên Temporal in-memory (time-skipping), activity giả ghi lại thứ tự gọi. */
class CheckoutWorkflowTest {

    private static final String ORDER = "o-1";

    private TestWorkflowEnvironment env;
    private WorkflowClient client;
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final FakePayments payments = new FakePayments();

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        // Như production: workflow ở checkout-service, activity order ở order-service, thanh toán ở payment-service.
        env.newWorker(TaskQueues.CHECKOUT_WORKFLOW).registerWorkflowImplementationTypes(CheckoutWorkflowImpl.class);
        env.newWorker(TaskQueues.CHECKOUT).registerActivitiesImplementations(new FakeSteps());
        env.newWorker(TaskQueues.PAYMENT).registerActivitiesImplementations(payments);
        env.start();
        client = env.getWorkflowClient();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private CheckoutWorkflow stub() {
        return client.newWorkflowStub(CheckoutWorkflow.class, WorkflowOptions.newBuilder()
                .setTaskQueue(TaskQueues.CHECKOUT_WORKFLOW)
                .setWorkflowId(CheckoutContract.workflowId(ORDER))
                .build());
    }

    private static CheckoutInput input(String method, boolean promotion) {
        return new CheckoutInput(ORDER, "c-1", new BigDecimal("225.00"), "USD", method, "tok", "https://shop/return",
                "1.2.3.4", promotion, 600);
    }

    @Test
    void happyPathRunsEveryStepInOrder() {
        CheckoutResult result = stub().run(input("STRIPE", true));

        assertThat(result.confirmed()).isTrue();
        assertThat(result.paymentId()).isEqualTo("pay-" + ORDER);
        assertThat(calls).containsExactly("redeemPromotion", "reserveInventory", "authorize", "commitInventory",
                "capture", "confirmOrder:pay-" + ORDER);
    }

    @Test
    void declinedPaymentCompensatesInReverseOrderAndCancelsOrder() {
        payments.decline = true;

        CheckoutResult result = stub().run(input("STRIPE", true));

        assertThat(result.confirmed()).isFalse();
        assertThat(result.failureReason()).isEqualTo("card declined");
        assertThat(calls).containsExactly("redeemPromotion", "reserveInventory", "authorize",
                "refund", "releaseInventory", "cancelPromotionRedemption", "cancelOrder:card declined");
    }

    @Test
    void redirectPaymentWaitsForSignalThenCompletes() {
        payments.redirect = true;
        CheckoutWorkflow wf = stub();
        CompletableFuture<CheckoutResult> run = WorkflowClient.execute(wf::run, input("PAYPAL", false));

        awaitStep(wf, CheckoutState.Step.AWAITING_PAYMENT);
        assertThat(wf.status().paymentRedirectUrl()).isEqualTo("https://pay/" + ORDER);
        wf.paymentCompleted(new PaymentCompleted("vnp-1", true, null));

        CheckoutResult result = run.join();
        assertThat(result.confirmed()).isTrue();
        assertThat(result.paymentId()).isEqualTo("vnp-1");
        assertThat(calls).containsExactly("reserveInventory", "authorize", "commitInventory", "capture",
                "confirmOrder:vnp-1");
    }

    @Test
    void redirectPaymentTimesOutAndCompensates() {
        payments.redirect = true;
        CheckoutWorkflow wf = stub();
        CompletableFuture<CheckoutResult> run = WorkflowClient.execute(wf::run, input("PAYPAL", false));

        awaitStep(wf, CheckoutState.Step.AWAITING_PAYMENT);
        env.sleep(Duration.ofMinutes(11));

        CheckoutResult result = run.join();
        assertThat(result.confirmed()).isFalse();
        assertThat(result.failureReason()).startsWith("payment not completed within 10 minutes");
        assertThat(calls).containsExactly("reserveInventory", "authorize", "refund", "releaseInventory",
                "cancelOrder:payment not completed within 10 minutes");
    }

    @Test
    void customerCancelWhileAwaitingPaymentCompensates() {
        payments.redirect = true;
        CheckoutWorkflow wf = stub();
        CompletableFuture<CheckoutResult> run = WorkflowClient.execute(wf::run, input("PAYPAL", false));

        awaitStep(wf, CheckoutState.Step.AWAITING_PAYMENT);
        wf.cancel("changed my mind");

        CheckoutResult result = run.join();
        assertThat(result.confirmed()).isFalse();
        assertThat(wf.status().step()).isEqualTo(CheckoutState.Step.CANCELLED);
        assertThat(calls).endsWith("refund", "releaseInventory", "cancelOrder:changed my mind");
    }

    private static void awaitStep(CheckoutWorkflow wf, CheckoutState.Step step) {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (wf.status().step() != step) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("workflow never reached " + step + ", now " + wf.status().step());
            }
            Thread.onSpinWait();
        }
    }

    private final class FakeSteps implements CheckoutActivities {
        @Override public void redeemPromotion(String orderId) { calls.add("redeemPromotion"); }
        @Override public void cancelPromotionRedemption(String orderId, String reason) { calls.add("cancelPromotionRedemption"); }
        @Override public String reserveInventory(String orderId) { calls.add("reserveInventory"); return CheckoutContract.reservationId(orderId); }
        @Override public void releaseInventory(String orderId, String reason) { calls.add("releaseInventory"); }
        @Override public void commitInventory(String orderId) { calls.add("commitInventory"); }
        @Override public void confirmOrder(String orderId, String paymentId) { calls.add("confirmOrder:" + paymentId); }
        @Override public void cancelOrder(String orderId, String reason) { calls.add("cancelOrder:" + reason); }
    }

    private final class FakePayments implements PaymentActivities {
        volatile boolean decline;
        volatile boolean redirect;

        @Override
        public PaymentAuthorization authorize(AuthorizePaymentRequest request) {
            calls.add("authorize");
            if (decline) {
                throw ApplicationFailure.newNonRetryableFailure("card declined", PaymentFailures.DECLINED);
            }
            String id = "pay-" + request.orderId();
            return redirect
                    ? new PaymentAuthorization(id, PaymentAuthorization.Status.ACTION_REQUIRED, "https://pay/" + request.orderId())
                    : new PaymentAuthorization(id, PaymentAuthorization.Status.AUTHORIZED, null);
        }

        @Override public void capture(String orderId) { calls.add("capture"); }
        @Override public void refund(RefundPaymentRequest request) { calls.add("refund"); }
    }
}
