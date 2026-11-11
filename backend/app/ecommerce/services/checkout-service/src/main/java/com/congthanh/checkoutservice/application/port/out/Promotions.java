package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Money;

/** Báo giá voucher (promotion-service). Redeem diễn ra trong saga ở order-service. */
public interface Promotions {

    Quote quote(String code, String customerId, Money subtotal);

    record Quote(boolean valid, Money discount, String reason) {
    }
}
