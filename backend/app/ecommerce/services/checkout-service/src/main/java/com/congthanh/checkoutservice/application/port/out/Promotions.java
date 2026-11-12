package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Money;

public interface Promotions {

    Quote quote(String code, String customerId, Money subtotal);

    record Quote(boolean valid, Money discount, String reason) {
    }
}
