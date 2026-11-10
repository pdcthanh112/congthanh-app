package com.congthanh.orderservice.adapter.in.workflow;

import com.congthanh.ecom.workflow.TaskQueues;
import com.congthanh.ecom.workflow.checkout.PaymentCompleted;
import com.congthanh.ecom.workflow.payment.AuthorizePaymentRequest;
import com.congthanh.ecom.workflow.payment.PaymentActivities;
import com.congthanh.ecom.workflow.payment.PaymentAuthorization;
import com.congthanh.ecom.workflow.payment.PaymentFailures;
import com.congthanh.ecom.workflow.payment.RefundPaymentRequest;
import com.congthanh.orderservice.adapter.in.workflow.CheckoutState.Step;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.TemporalFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.time.Duration;

/**
 * Saga checkout (orchestration). Trạng thái saga nằm trong lịch sử event của Temporal, nên
 * pod chết giữa chừng thì worker khác replay và chạy tiếp đúng bước đó; không có bảng saga tự viết.
 *
 * <pre>
 *  redeem voucher ─► giữ hàng ─► authorize ─► [chờ khách trả tiền ở cổng] ─► commit kho ─► capture ─► confirm
 *       │                │            │                 │                       ▲
 *       └──── lỗi ở bất kỳ bước nào trước commit: bù trừ ngược (refund ◄ release ◄ cancel voucher) rồi huỷ đơn
 * </pre>
 *
 * Commit kho là bước "pivot": sau đó saga chỉ đi tiếp (capture, confirm retry đến khi xong), không bù trừ.
 * Đổi logic workflow sau khi đã chạy production phải dùng {@code Workflow.getVersion} để không vỡ replay.
 */
public class CheckoutWorkflowImpl implements CheckoutWorkflow {

    private static final Logger log = Workflow.getLogger(CheckoutWorkflowImpl.class);
    private static final Duration DEFAULT_PAYMENT_TIMEOUT = Duration.ofMinutes(15);

    private static final RetryOptions FORWARD_RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2)
            .setMaximumInterval(Duration.ofSeconds(30))
            .setMaximumAttempts(6)
            .setDoNotRetry(CheckoutActivitiesImpl.REJECTED, PaymentFailures.DECLINED, PaymentFailures.UNSUPPORTED_METHOD)
            .build();

    /** Bù trừ và các bước sau pivot phải xong bằng mọi giá: retry không giới hạn, backoff tới 5 phút. */
    private static final RetryOptions MUST_COMPLETE_RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2)
            .setMaximumInterval(Duration.ofMinutes(5))
            .setMaximumAttempts(0)
            .build();

    private final CheckoutActivities steps = Workflow.newActivityStub(CheckoutActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CHECKOUT)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(FORWARD_RETRY)
                    .build());

    private final CheckoutActivities mustComplete = Workflow.newActivityStub(CheckoutActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.CHECKOUT)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(MUST_COMPLETE_RETRY)
                    .build());

    private final PaymentActivities payments = Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.PAYMENT)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setScheduleToCloseTimeout(Duration.ofMinutes(3))
                    .setRetryOptions(FORWARD_RETRY)
                    .build());

    private final PaymentActivities paymentsMustComplete = Workflow.newActivityStub(PaymentActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.PAYMENT)
                    .setStartToCloseTimeout(Duration.ofSeconds(30))
                    .setRetryOptions(MUST_COMPLETE_RETRY)
                    .build());

    private Step step = Step.STARTED;
    private String paymentRedirectUrl;
    private String failureReason;
    private PaymentCompleted paymentResult;
    private String cancelRequest;

    @Override
    public CheckoutResult run(CheckoutInput in) {
        String orderId = in.orderId();
        Saga saga = new Saga(new Saga.Options.Builder().setContinueWithError(true).build());
        String paymentId = null;
        try {
            if (in.hasPromotion()) {
                step = Step.REDEEMING_PROMOTION;
                // Đăng ký bù trừ TRƯỚC khi gọi: nếu activity đã chạy xong ở phía kia nhưng timeout ở đây,
                // ta vẫn trả lại. Các lệnh bù trừ đều idempotent nên gọi thừa là vô hại.
                saga.addCompensation(mustComplete::cancelPromotionRedemption, orderId, "checkout cancelled");
                steps.redeemPromotion(orderId);
            }
            abortIfCancelRequested();

            step = Step.RESERVING_INVENTORY;
            saga.addCompensation(mustComplete::releaseInventory, orderId, "checkout cancelled");
            steps.reserveInventory(orderId);
            abortIfCancelRequested();

            step = Step.AUTHORIZING_PAYMENT;
            saga.addCompensation(paymentsMustComplete::refund, new RefundPaymentRequest(orderId, "checkout cancelled"));
            PaymentAuthorization auth = payments.authorize(new AuthorizePaymentRequest(orderId, in.customerId(),
                    in.amount(), in.currency(), in.paymentMethod(), in.paymentToken(), in.returnUrl(), in.clientIp()));
            paymentId = auth.paymentId();

            if (auth.status() == PaymentAuthorization.Status.ACTION_REQUIRED) {
                step = Step.AWAITING_PAYMENT;
                paymentRedirectUrl = auth.redirectUrl();
                Duration timeout = in.paymentTimeoutSeconds() > 0
                        ? Duration.ofSeconds(in.paymentTimeoutSeconds()) : DEFAULT_PAYMENT_TIMEOUT;
                // Timer bền vững: không giữ thread, sống qua restart/deploy.
                boolean answered = Workflow.await(timeout, () -> paymentResult != null || cancelRequest != null);
                abortIfCancelRequested();
                if (!answered) {
                    throw new CheckoutAborted("payment not completed within " + timeout.toMinutes() + " minutes");
                }
                if (!paymentResult.success()) {
                    throw new CheckoutAborted("payment failed: " + paymentResult.reason());
                }
                if (paymentResult.paymentId() != null) {
                    paymentId = paymentResult.paymentId();
                }
                paymentRedirectUrl = null;
            }
            abortIfCancelRequested();

            // Pivot: từ đây không bù trừ nữa.
            step = Step.COMMITTING_INVENTORY;
            steps.commitInventory(orderId);
        } catch (ActivityFailure | CheckoutAborted e) {
            return compensate(orderId, saga, e);
        }

        step = Step.CAPTURING_PAYMENT;
        paymentsMustComplete.capture(orderId);
        mustComplete.confirmOrder(orderId, paymentId);
        step = Step.COMPLETED;
        return new CheckoutResult(orderId, true, paymentId, null);
    }

    private CheckoutResult compensate(String orderId, Saga saga, Exception cause) {
        failureReason = reason(cause);
        log.info("checkout {} failed at {}: {}", orderId, step, failureReason);
        step = Step.COMPENSATING;
        paymentRedirectUrl = null;
        saga.compensate();
        mustComplete.cancelOrder(orderId, failureReason);
        step = Step.CANCELLED;
        return new CheckoutResult(orderId, false, null, failureReason);
    }

    @Override
    public void paymentCompleted(PaymentCompleted result) {
        if (paymentResult == null) {
            paymentResult = result;
        }
    }

    @Override
    public void cancel(String reason) {
        // Chỉ có tác dụng trước pivot; sau khi commit kho thì đơn đi tới cùng.
        if (cancelRequest == null) {
            cancelRequest = reason == null || reason.isBlank() ? "cancelled by customer" : reason;
        }
    }

    @Override
    public CheckoutState status() {
        return new CheckoutState(step, paymentRedirectUrl, failureReason);
    }

    private void abortIfCancelRequested() {
        if (cancelRequest != null) {
            throw new CheckoutAborted(cancelRequest);
        }
    }

    private static String reason(Exception e) {
        Throwable t = e;
        while (t != null) {
            if (t instanceof ApplicationFailure af) {
                return af.getOriginalMessage();
            }
            if (t instanceof CheckoutAborted) {
                return t.getMessage();
            }
            if (t.getCause() == null && t instanceof TemporalFailure tf) {
                return tf.getOriginalMessage();
            }
            t = t.getCause();
        }
        return e.getMessage();
    }

    /** Huỷ do workflow tự quyết (khách huỷ, hết hạn chờ thanh toán), không phải lỗi activity. */
    static final class CheckoutAborted extends RuntimeException {
        CheckoutAborted(String message) {
            super(message);
        }
    }
}
