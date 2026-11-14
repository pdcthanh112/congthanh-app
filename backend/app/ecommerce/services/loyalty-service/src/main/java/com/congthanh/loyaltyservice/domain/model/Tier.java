package com.congthanh.loyaltyservice.domain.model;

/** Hạng thành viên theo chi tiêu 12 tháng (đơn đã qua hạn trả hàng); hạng cao tích điểm nhanh hơn. */
public enum Tier {
    MEMBER(0, 100), SILVER(500, 125), GOLD(1500, 150);

    private final long minSpendUsd;
    private final int earnPercent;

    Tier(long minSpendUsd, int earnPercent) {
        this.minSpendUsd = minSpendUsd;
        this.earnPercent = earnPercent;
    }

    public long minSpendUsd() {
        return minSpendUsd;
    }

    /** Phần trăm điểm so với mức cơ bản (100 = 1 điểm / 1 USD). */
    public int earnPercent() {
        return earnPercent;
    }

    public static Tier forSpend(java.math.BigDecimal spend) {
        Tier t = MEMBER;
        for (Tier x : values()) {
            if (spend.compareTo(java.math.BigDecimal.valueOf(x.minSpendUsd)) >= 0) {
                t = x;
            }
        }
        return t;
    }
}
