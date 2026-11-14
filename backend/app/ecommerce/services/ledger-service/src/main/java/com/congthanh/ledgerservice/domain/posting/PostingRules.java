package com.congthanh.ledgerservice.domain.posting;

import com.congthanh.ledgerservice.domain.exception.LedgerException;
import com.congthanh.ledgerservice.domain.model.JournalEntry;
import com.congthanh.ledgerservice.domain.model.Posting;

import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Luật hạch toán (version {@value #VERSION}). Mỗi sự kiện tài chính thành đúng một bút toán cân.
 *
 * <pre>
 * PaymentCaptured  Nợ asset:psp_clearing:&lt;cổng&gt;      Có revenue:gross_sales
 * PaymentRefunded  Nợ contra_revenue:refunds            Có asset:psp_clearing:&lt;cổng&gt;
 *
 * v2 (ADR 0025):
 * StoredValueCredited  Nợ contra_revenue:refunds (hoàn hàng) | expense:goodwill | expense:referral_rewards
 *                      | expense:stored_value_adjustments     Có liability:stored_value:&lt;kind&gt;
 * StoredValueDebited   Nợ liability:stored_value:&lt;kind&gt;      Có revenue:gross_sales
 * DisputeOpened        Nợ asset:disputed_funds:&lt;cổng&gt;, expense:dispute_fees   Có asset:psp_clearing:&lt;cổng&gt;
 * DisputeClosed WON    Nợ asset:psp_clearing:&lt;cổng&gt;       Có asset:disputed_funds:&lt;cổng&gt;
 * DisputeClosed LOST   Nợ expense:chargeback_losses         Có asset:disputed_funds:&lt;cổng&gt;
 *
 * v3 (ADR 0026), điểm thưởng theo giá trị quy đổi:
 * Points CONFIRMED            Nợ expense:loyalty_program       Có liability:loyalty_points
 * Points REDEEMED             Nợ liability:loyalty_points      Có revenue:gross_sales
 * Points REVERSED / EXPIRED   Nợ liability:loyalty_points      Có expense:loyalty_program
 * ProviderFees (phần chênh)   Nợ expense:psp_fees:&lt;cổng&gt;      Có asset:psp_clearing:&lt;cổng&gt; (âm thì đảo chiều)
 * </pre>
 *
 * <p>Giai đoạn đầu ghi doanh thu gộp cả thuế bán hàng và phí ship vì event thanh toán chưa tách các phần đó;
 * khi order/tax phát phần thuế theo bang, luật v2 sẽ tách sang {@code liability:sales_tax_payable:<bang>}.
 * Ngày hạch toán theo America/New_York (ADR 0015).
 */
public final class PostingRules {

    public static final String VERSION = "3";
    public static final ZoneId BOOKING_ZONE = ZoneId.of("America/New_York");

    static final String GROSS_SALES = "revenue:gross_sales";
    static final String REFUNDS = "contra_revenue:refunds";

    private PostingRules() {
    }

    public static JournalEntry post(FinancialFact fact, String entryId, Instant now) {
        LocalDate date = LocalDate.ofInstant(fact.occurredAt() == null ? now : fact.occurredAt(), BOOKING_ZONE);
        return switch (fact) {
            case FinancialFact.PaymentCaptured c -> new JournalEntry(entryId, "payment", c.paymentId(), c.eventId(),
                    "Captured payment for order " + c.orderId(), date, List.of(
                    Posting.debit(clearing(c.provider()), amount(c.amount()), c.currency()),
                    Posting.credit(GROSS_SALES, amount(c.amount()), c.currency())), "system", now);
            case FinancialFact.PaymentRefunded r -> new JournalEntry(entryId, "refund", r.refundId(), r.eventId(),
                    "Refund " + r.refundId() + " for order " + r.orderId(), date, List.of(
                    Posting.debit(REFUNDS, amount(r.amount()), r.currency()),
                    Posting.credit(clearing(r.provider()), amount(r.amount()), r.currency())), "system", now);
            case FinancialFact.StoredValueCredited c -> new JournalEntry(entryId, "stored_value_credit", c.reference(),
                    c.eventId(), "Store credit " + c.reason() + " " + c.reference(), date, List.of(
                    Posting.debit(creditSource(c.reason()), amount(c.amount()), c.currency()),
                    Posting.credit(storedValue(c.kind()), amount(c.amount()), c.currency())), "system", now);
            case FinancialFact.StoredValueDebited d -> new JournalEntry(entryId, "stored_value_debit", d.holdId(),
                    d.eventId(), "Store credit spent on order " + d.orderId(), date, List.of(
                    Posting.debit(storedValue(d.kind()), amount(d.amount()), d.currency()),
                    Posting.credit(GROSS_SALES, amount(d.amount()), d.currency())), "system", now);
            case FinancialFact.LoyaltyPoints p -> {
                String debit = switch (p.kind()) {
                    case "CONFIRMED" -> "expense:loyalty_program";
                    case "REDEEMED", "REVERSED", "EXPIRED" -> "liability:loyalty_points";
                    default -> throw LedgerException.invalid("no posting rule for loyalty " + p.kind());
                };
                String credit = switch (p.kind()) {
                    case "CONFIRMED" -> "liability:loyalty_points";
                    case "REDEEMED" -> GROSS_SALES;
                    default -> "expense:loyalty_program";
                };
                yield new JournalEntry(entryId, "loyalty_" + p.kind().toLowerCase(Locale.ROOT), p.reference(), p.eventId(),
                        p.points() + " points " + p.kind() + " for " + p.customerId(), date, List.of(
                        Posting.debit(debit, amount(p.value()), p.currency()),
                        Posting.credit(credit, amount(p.value()), p.currency())), "system", now);
            }
            case FinancialFact.ProviderFees f -> {
                java.math.BigDecimal delta = f.fees().subtract(f.alreadyPosted() == null ? java.math.BigDecimal.ZERO : f.alreadyPosted());
                if (delta.signum() == 0) {
                    throw LedgerException.conflict("provider fees for " + f.provider() + " " + f.businessDate() + " already posted");
                }
                String expense = "expense:psp_fees:" + clearing(f.provider()).substring("asset:psp_clearing:".length());
                Posting a = delta.signum() > 0 ? Posting.debit(expense, amount(delta), f.currency())
                        : Posting.credit(expense, amount(delta.negate()), f.currency());
                Posting b = delta.signum() > 0 ? Posting.credit(clearing(f.provider()), amount(delta), f.currency())
                        : Posting.debit(clearing(f.provider()), amount(delta.negate()), f.currency());
                yield new JournalEntry(entryId, "psp_fees", feeSource(f.provider(), f.businessDate()), f.eventId(),
                        "Provider fees " + f.provider() + " " + f.businessDate(), LocalDate.parse(f.businessDate()),
                        List.of(a, b), "system", now);
            }
            case FinancialFact.DisputeOpened o -> {
                var postings = new java.util.ArrayList<Posting>();
                postings.add(Posting.debit(disputed(o.provider()), amount(o.amount()), o.currency()));
                postings.add(Posting.credit(clearing(o.provider()), amount(o.amount()), o.currency()));
                if (o.fee() != null && o.fee().signum() > 0) {
                    postings.add(Posting.debit("expense:dispute_fees", amount(o.fee()), o.currency()));
                    postings.add(Posting.credit(clearing(o.provider()), amount(o.fee()), o.currency()));
                }
                yield new JournalEntry(entryId, "dispute", o.disputeId(), o.eventId(),
                        "Dispute opened for order " + o.orderId(), date, postings, "system", now);
            }
            case FinancialFact.DisputeClosed c -> {
                boolean won = "WON".equals(c.outcome());
                yield new JournalEntry(entryId, "dispute_closed", c.disputeId(), c.eventId(),
                        "Dispute " + c.disputeId() + " " + c.outcome(), date, List.of(
                        Posting.debit(won ? clearing(c.provider()) : "expense:chargeback_losses", amount(c.amount()), c.currency()),
                        Posting.credit(disputed(c.provider()), amount(c.amount()), c.currency())), "system", now);
            }
        };
    }

    /** Tài khoản trung gian của cổng: tiền cổng đang giữ, chưa chuyển về ngân hàng. */
    static String clearing(String provider) {
        if (provider == null || provider.isBlank()) {
            throw LedgerException.invalid("payment event has no provider; cannot pick a clearing account");
        }
        return "asset:psp_clearing:" + provider.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    public static String feeSource(String provider, String businessDate) {
        return provider.toLowerCase(Locale.ROOT) + ":" + businessDate;
    }

    static String storedValue(String kind) {
        return "liability:stored_value:" + (kind == null || kind.isBlank() ? "store_credit" : kind.toLowerCase(Locale.ROOT));
    }

    static String disputed(String provider) {
        return "asset:disputed_funds:" + clearing(provider).substring("asset:psp_clearing:".length());
    }

    static String creditSource(String reason) {
        return switch (reason == null ? "" : reason) {
            case "RETURN_REFUND" -> REFUNDS;
            case "GOODWILL" -> "expense:goodwill";
            case "REFERRAL_REWARD" -> "expense:referral_rewards";
            case "ADJUSTMENT" -> "expense:stored_value_adjustments";
            default -> throw LedgerException.invalid("no posting rule for store credit reason " + reason);
        };
    }

    private static java.math.BigDecimal amount(java.math.BigDecimal a) {
        if (a == null) {
            throw LedgerException.invalid("payment event has no amount");
        }
        return a.setScale(4, RoundingMode.HALF_EVEN);
    }
}
