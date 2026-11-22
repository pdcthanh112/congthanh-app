package com.congthanh.checkoutservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.TaskQueues;
import com.congthanh.ecommerce.workflow.checkout.CheckoutActivities;
import com.congthanh.ecommerce.workflow.checkout.CheckoutInput;
import com.congthanh.ecommerce.workflow.checkout.CheckoutResult;
import com.congthanh.ecommerce.workflow.checkout.CheckoutState;
import com.congthanh.ecommerce.workflow.checkout.CheckoutState.Step;
import com.congthanh.ecommerce.workflow.checkout.PaymentCompleted;
import com.congthanh.ecommerce.workflow.payment.AuthorizePaymentRequest;
import com.congthanh.ecommerce.workflow.payment.PaymentActivities;
import com.congthanh.ecommerce.workflow.payment.PaymentAuthorization;
import com.congthanh.ecommerce.workflow.payment.PaymentFailures;
import com.congthanh.ecommerce.workflow.payment.RefundPaymentRequest;
import com.congthanh.ecommerce.workflow.loyalty.LoyaltyActivities;
import com.congthanh.ecommerce.workflow.storedvalue.StoredValueActivities;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ActivityFailure;
import io.temporal.failure.ApplicationFailure;
import io.temporal.failure.TemporalFailure;
import io.temporal.workflow.Saga;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

import java.math.BigDecimal;
import java.time.Duration;

/**
 * Saga checkout (orchestration), chạy ở checkout-service trên task queue {@link TaskQueues#CHECKOUT_WORKFLOW}.
 * Các bước gọi activity của order-service (queue {@link TaskQueues#CHECKOUT}) và payment-service
 * (queue {@link TaskQueues#PAYMENT}). Trạng thái saga nằm trong lịch sử event của Temporal, nên
 * pod chết giữa chừng thì worker khác replay và chạy tiếp đúng bước đó; không có bảng saga tự viết.
 *
 * <pre>
 *  redeem voucher ─► giữ hàng ─► giữ điểm + store credit ─► authorize phần còn lại ─► [chờ khách trả tiền ở cổng]
 *  ─► capture ─► commit kho ─► trừ hẳn điểm + store credit ─► confirm
 *       │                │            │                 │                     │            │
 *       └──── lỗi ở bất kỳ bước nào tới commit kho: bù trừ ngược (refund ◄ release ◄ cancel voucher) rồi huỷ đơn
 * </pre>
 *
 * Capture đứng trước commit kho vì tiền hoàn lại được (refund), còn hàng đã commit thì không trả về kho được.
 * Commit kho là bước "pivot": retry lỗi tạm thời tới khi xong, chỉ bù trừ khi inventory từ chối hẳn (chắc chắn
 * chưa commit). Sau pivot chỉ còn confirm; nếu confirm bị từ chối hẳn thì hoàn tiền, huỷ đơn và ghi log lỗi để
 * vận hành xử lý phần hàng đã xuất, chứ không để workflow fail với tiền đã trừ.
 * Đổi logic workflow sau khi đã chạy production phải dùng {@code Workflow.getVersion} để không vỡ replay.
 */
public class CheckoutWorkflowImpl implements CheckoutWorkflow {

    private static final Logger log = Workflow.getLogger(CheckoutWorkflowImpl.class);
    private static final Duration DEFAULT_PAYMENT_TIMEOUT = Duration.ofMinutes(15);
    static final String CAPTURE_BEFORE_COMMIT = "capture-before-commit";
    static final String TENDERS = "store-credit-and-loyalty";

    private static final RetryOptions FORWARD_RETRY = RetryOptions.newBuilder()
            .setInitialInterval(Duration.ofSeconds(1))
            .setBackoffCoefficient(2)
            .setMaximumInterval(Duration.ofSeconds(30))
            .setMaximumAttempts(6)
            .setDoNotRetry(CheckoutActivities.REJECTED, PaymentFailures.DECLINED, PaymentFailures.UNSUPPORTED_METHOD)
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

    private final StoredValueActivities storedValue = Workflow.newActivityStub(StoredValueActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.STORED_VALUE)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2).setMaximumInterval(Duration.ofSeconds(30)).setMaximumAttempts(6)
                            .setDoNotRetry(StoredValueActivities.REJECTED).build())
                    .build());

    private final StoredValueActivities storedValueMustComplete = Workflow.newActivityStub(StoredValueActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.STORED_VALUE)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(MUST_COMPLETE_RETRY)
                    .build());

    private final LoyaltyActivities loyalty = Workflow.newActivityStub(LoyaltyActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.LOYALTY)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
                    .setRetryOptions(RetryOptions.newBuilder().setInitialInterval(Duration.ofSeconds(1))
                            .setBackoffCoefficient(2).setMaximumInterval(Duration.ofSeconds(30)).setMaximumAttempts(6)
                            .setDoNotRetry(LoyaltyActivities.REJECTED).build())
                    .build());

    private final LoyaltyActivities loyaltyMustComplete = Workflow.newActivityStub(LoyaltyActivities.class,
            ActivityOptions.newBuilder()
                    .setTaskQueue(TaskQueues.LOYALTY)
                    .setStartToCloseTimeout(Duration.ofSeconds(15))
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

            boolean tenders = Workflow.getVersion(TENDERS, Workflow.DEFAULT_VERSION, 1) != Workflow.DEFAULT_VERSION;
            BigDecimal payable = in.amount();
            if (tenders) {
                payable = reserveTenders(in, saga);
                abortIfCancelRequested();
            }
            boolean charge = !tenders || payable.signum() > 0;

            PaymentAuthorization auth = null;
            if (charge) {
                step = Step.AUTHORIZING_PAYMENT;
                saga.addCompensation(paymentsMustComplete::refund, new RefundPaymentRequest(orderId, "checkout cancelled"));
                auth = payments.authorize(new AuthorizePaymentRequest(orderId, in.customerId(),
                        payable, in.currency(), in.paymentMethod(), in.paymentToken(), in.returnUrl(), in.clientIp()));
                paymentId = auth.paymentId();
            }

            if (auth != null && auth.status() == PaymentAuthorization.Status.ACTION_REQUIRED) {
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

            if (Workflow.getVersion(CAPTURE_BEFORE_COMMIT, Workflow.DEFAULT_VERSION, 1) == Workflow.DEFAULT_VERSION) {
                return legacyCommitThenCapture(orderId, paymentId, saga);
            }

            // Capture có thể bị từ chối (authorization hết hạn...): vẫn còn bù trừ được, refund lo cả hai trường hợp.
            if (charge) {
                step = Step.CAPTURING_PAYMENT;
                payments.capture(orderId);
            }

            // Pivot. Lỗi tạm thời thì retry mãi; chỉ lỗi nghiệp vụ (giữ chỗ đã hết hạn/đã nhả) mới tới catch.
            step = Step.COMMITTING_INVENTORY;
            mustComplete.commitInventory(orderId);
        } catch (ActivityFailure | CheckoutAborted e) {
            return compensate(orderId, saga, e);
        }

        // Sau pivot: trừ hẳn store credit và điểm đã giữ (phải xong, retry mãi).
        if (in.usesStoreCredit() && heldStoreCredit) {
            storedValueMustComplete.capture(orderId);
        }
        if (in.usesLoyaltyPoints() && reservedPoints) {
            loyaltyMustComplete.commit(orderId);
        }

        try {
            mustComplete.confirmOrder(orderId, paymentId);
        } catch (ActivityFailure e) {
            failureReason = reason(e);
            log.error("checkout {}: confirm rejected after inventory commit ({}); refunding and cancelling,"
                    + " committed stock needs manual review", orderId, failureReason);
            step = Step.COMPENSATING;
            paymentsMustComplete.refund(new RefundPaymentRequest(orderId, "order confirmation failed"));
            mustComplete.cancelOrder(orderId, failureReason);
            step = Step.CANCELLED;
            return new CheckoutResult(orderId, false, null, failureReason);
        }
        step = Step.COMPLETED;
        return new CheckoutResult(orderId, true, paymentId, null);
    }

    private boolean heldStoreCredit;
    private boolean reservedPoints;

    /**
     * Giữ điểm thưởng và store credit (hold id / redemption id = order id, idempotent), đăng ký bù trừ trước khi gọi.
     *
     * @return số tiền còn phải charge cổng thanh toán
     */
    private BigDecimal reserveTenders(CheckoutInput in, Saga saga) {
        BigDecimal payable = in.amount();
        step = Step.RESERVING_TENDERS;
        if (in.usesLoyaltyPoints()) {
            saga.addCompensation(loyaltyMustComplete::release, in.orderId());
            BigDecimal value = loyalty.reserve(in.orderId(), in.customerId(), in.orderId(), in.loyaltyPoints(),
                    in.currency());
            reservedPoints = true;
            payable = payable.subtract(value);
        }
        if (in.usesStoreCredit()) {
            saga.addCompensation(storedValueMustComplete::release, in.orderId(), "checkout cancelled");
            storedValue.hold(new StoredValueActivities.HoldRequest(in.customerId(), in.orderId(), in.orderId(),
                    in.storeCredit(), in.currency()));
            heldStoreCredit = true;
            payable = payable.subtract(in.storeCredit());
        }
        if (payable.signum() < 0) {
            throw new CheckoutAborted("store credit and loyalty points exceed the order total by " + payable.negate());
        }
        return payable;
    }

    /** Thứ tự cũ (commit kho rồi mới capture), chỉ để replay workflow bắt đầu trước khi đổi. */
    private CheckoutResult legacyCommitThenCapture(String orderId, String paymentId, Saga saga) {
        try {
            step = Step.COMMITTING_INVENTORY;
            steps.commitInventory(orderId);
        } catch (ActivityFailure e) {
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
        // Chỉ có tác dụng trước capture; sau đó đơn đi tới cùng.
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
