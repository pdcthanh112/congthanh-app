package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Money;

import java.util.List;
import java.util.Map;

public interface Pricing {

    Result quote(Map<String, Integer> quantities, String currency, List<String> customerGroups);

    record Line(String productId, int quantity, Money unitPrice, Money compareAt, Money lineTotal) {
    }

    record Result(Map<String, Line> lines, List<String> unpriced) {
    }
}
