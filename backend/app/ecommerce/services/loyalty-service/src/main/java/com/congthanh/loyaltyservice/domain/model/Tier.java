package com.congthanh.loyaltyservice.domain.model;

/** Hạng thành viên theo chi tiêu 12 tháng (đơn đã qua hạn trả hàng); hạng cao tích điểm nhanh hơn. */
public enum Tier {
    MEMBER(0, 100), SILVER(500, 125), GOLD(1500, 150);

    private final long minSpend;
    private final int earnPercent;

    Tier(long minSpend, int earnPercent) {
        this.minSpend = minSpend;
        this.earnPercent = earnPercent;
    }

    public long minSpend() {
        return minSpend;
    }

    /** Phần trăm điểm so với mức cơ bản (100 = 1 điểm cho mỗi 1 đơn vị tiền của chương trình). */
    public int earnPercent() {
        return earnPercent;
    }

    public static Tier forSpend(java.math.BigDecimal spend) {
        Tier t = MEMBER;
        for (Tier x : values()) {
            if (spend.compareTo(java.math.BigDecimal.valueOf(x.minSpend)) >= 0) {
                t = x;
            }
        }
        return t;
    }
}
