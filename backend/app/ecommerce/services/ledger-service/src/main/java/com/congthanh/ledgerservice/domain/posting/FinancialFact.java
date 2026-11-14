package com.congthanh.ledgerservice.domain.posting;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Sự kiện tài chính đã được dịch từ event Kafka sang ngôn ngữ của sổ cái (adapter làm việc dịch,
 * domain không biết protobuf). Mỗi loại có một luật hạch toán trong {@link PostingRules}.
 */
public sealed interface FinancialFact {

    String eventId();

    Instant occurredAt();

    /** Tiền đã thu từ khách qua cổng thanh toán. */
    record PaymentCaptured(String eventId, Instant occurredAt, String paymentId, String orderId, String provider,
                           BigDecimal amount, String currency) implements FinancialFact {
    }

    /** Cộng store credit/gift card: nợ phải trả tăng. reason: RETURN_REFUND, GOODWILL, REFERRAL_REWARD, ADJUSTMENT. */
    record StoredValueCredited(String eventId, Instant occurredAt, String accountId, String kind, String reason,
                               String reference, BigDecimal amount, String currency) implements FinancialFact {
    }

    /** Khách tiêu store credit/gift card cho một đơn: nợ phải trả chuyển thành doanh thu. */
    record StoredValueDebited(String eventId, Instant occurredAt, String accountId, String kind, String holdId,
                              String orderId, BigDecimal amount, String currency) implements FinancialFact {
    }

    /** Cổng rút tiền tranh chấp (và thu phí) khỏi số dư. */
    record DisputeOpened(String eventId, Instant occurredAt, String disputeId, String provider, String orderId,
                         BigDecimal amount, BigDecimal fee, String currency) implements FinancialFact {
    }

    /** Kết quả tranh chấp: WON tiền về lại; LOST/ACCEPTED mất hẳn. */
    record DisputeClosed(String eventId, Instant occurredAt, String disputeId, String provider, String outcome,
                         BigDecimal amount, String currency) implements FinancialFact {
    }

    /**
     * Điểm thưởng thay đổi giá trị nghĩa vụ. kind: CONFIRMED (điểm thành khả dụng), REDEEMED (tiêu cho đơn),
     * REVERSED (đảo vì trả hàng, chỉ điểm đã khả dụng), EXPIRED.
     */
    record LoyaltyPoints(String eventId, Instant occurredAt, String kind, String customerId, String reference,
                         long points, BigDecimal value, String currency) implements FinancialFact {
    }

    /**
     * Tổng phí cổng của một ngày theo đối soát (reconciliation-service). Đối soát chạy lại cùng ngày thì chỉ hạch
     * toán phần chênh so với số đã ghi ({@code alreadyPosted}, application tính từ sổ).
     */
    record ProviderFees(String eventId, Instant occurredAt, String provider, String businessDate, BigDecimal fees,
                        BigDecimal alreadyPosted, String currency) implements FinancialFact {

        public ProviderFees withAlreadyPosted(BigDecimal posted) {
            return new ProviderFees(eventId, occurredAt, provider, businessDate, fees, posted, currency);
        }
    }

    /** Một lần hoàn tiền cho khách qua cổng thanh toán. */
    record PaymentRefunded(String eventId, Instant occurredAt, String paymentId, String orderId, String provider,
                           String refundId, BigDecimal amount, String currency) implements FinancialFact {
    }
}
