package com.congthanh.disputeservice.domain.model;

import java.util.Map;

/**
 * Gói bằng chứng nộp cho cổng. {@code fields} dùng tên trường của Stripe (shipping_tracking_number,
 * refund_refusal_explanation...) vì đó là tập phổ biến nhất; adapter PayPal sẽ ánh xạ lại.
 *
 * @param recommendation FIGHT nếu bằng chứng đủ mạnh, ACCEPT nếu chống gần như chắc thua (tiết kiệm công sức)
 * @param rationale      giải thích cho người xử lý vì sao khuyến nghị như vậy
 */
public record Evidence(Map<String, String> fields, Recommendation recommendation, String rationale) {

    public enum Recommendation { FIGHT, ACCEPT, REVIEW }
}
