package com.congthanh.promotionservice.domain.model;

/** Nhóm marketing (để báo cáo/hiển thị). Cách tính tiền nằm ở {@link DiscountRule}. */
public enum PromotionType {
    DISCOUNT, SALE, VOUCHER, BUY_ONE_GET_ONE, FLASH_SALE, FREE_SHIPPING, LOYALTY_PROGRAM, BUNDLE_DEAL,
    FIRST_PURCHASE_DISCOUNT, REFERRAL_DISCOUNT, SEASONAL_PROMOTION, CASHBACK
}
