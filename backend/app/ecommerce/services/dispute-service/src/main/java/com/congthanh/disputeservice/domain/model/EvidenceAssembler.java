package com.congthanh.disputeservice.domain.model;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Gom bằng chứng tự động theo lý do tranh chấp. Thuần (không I/O) để kiểm thử được từng tình huống.
 * <ul>
 *   <li>Đã hoàn đủ trước khi tranh chấp → FIGHT bằng "đã hoàn tiền" (gần như chắc thắng).</li>
 *   <li>PRODUCT_NOT_RECEIVED + hãng xác nhận đã giao → FIGHT bằng vận đơn và ngày giao.</li>
 *   <li>FRAUDULENT + chưa giao hoặc không có vận đơn → ACCEPT (thường thua, tốn công).</li>
 *   <li>Còn lại → REVIEW: người xử lý quyết định.</li>
 * </ul>
 */
public final class EvidenceAssembler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE.withZone(ZoneOffset.UTC);

    private EvidenceAssembler() {
    }

    public static Evidence assemble(Dispute d, OrderFacts facts, String refundPolicy) {
        Map<String, String> f = new LinkedHashMap<>();
        if (facts == null) {
            f.put("uncategorized_text", "Order " + d.orderId() + ": no order facts recorded yet.");
            return new Evidence(f, Evidence.Recommendation.REVIEW, "No order, shipping or refund facts are known yet.");
        }
        put(f, "customer_name", facts.recipientName());
        put(f, "product_description", facts.productIds().isEmpty() ? null : "Products: " + String.join(", ", facts.productIds()));
        put(f, "shipping_carrier", facts.carrier());
        put(f, "shipping_tracking_number", facts.trackingNumber());
        if (facts.shippedAt() != null) {
            f.put("shipping_date", DATE.format(facts.shippedAt()));
        }
        if (facts.shippingRegion() != null) {
            put(f, "shipping_address", (facts.shippingRegion() + " " + nz(facts.shippingPostalCode())).trim());
        }
        put(f, "refund_policy", refundPolicy);

        BigDecimal refunded = facts.refunded() == null ? BigDecimal.ZERO : facts.refunded();
        if (refunded.compareTo(d.amount()) >= 0) {
            f.put("refund_refusal_explanation", "The customer was already refunded " + refunded + " " + d.currency()
                    + (facts.lastRefundAt() == null ? "" : " on " + DATE.format(facts.lastRefundAt()))
                    + ", so the disputed amount was returned before this dispute.");
            return new Evidence(f, Evidence.Recommendation.FIGHT, "Order was fully refunded before the dispute.");
        }
        return switch (d.reason()) {
            case PRODUCT_NOT_RECEIVED -> facts.delivered()
                    ? withText(f, "Carrier " + nz(facts.carrier()) + " confirmed delivery on " + DATE.format(facts.deliveredAt())
                    + " (tracking " + nz(facts.trackingNumber()) + ").", Evidence.Recommendation.FIGHT,
                    "Carrier confirmed delivery.")
                    : new Evidence(f, Evidence.Recommendation.ACCEPT, "No proof of delivery: likely to lose.");
            case FRAUDULENT -> facts.delivered()
                    ? new Evidence(f, Evidence.Recommendation.REVIEW,
                    "Delivered to the address on the order; check fraud score and account history before fighting.")
                    : new Evidence(f, Evidence.Recommendation.ACCEPT, "Fraud claim without proof of delivery: likely to lose.");
            case CREDIT_NOT_PROCESSED -> refunded.signum() > 0
                    ? withText(f, "A partial refund of " + refunded + " " + d.currency() + " was issued.",
                    Evidence.Recommendation.REVIEW, "Only part of the amount was refunded.")
                    : new Evidence(f, Evidence.Recommendation.REVIEW, "Check the return request for this order.");
            default -> new Evidence(f, Evidence.Recommendation.REVIEW, "Needs a person to decide.");
        };
    }

    private static Evidence withText(Map<String, String> f, String text, Evidence.Recommendation r, String why) {
        f.put("uncategorized_text", text);
        return new Evidence(f, r, why);
    }

    private static void put(Map<String, String> f, String k, String v) {
        if (v != null && !v.isBlank()) {
            f.put(k, v);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
