package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.Money;

import java.util.List;
import java.util.Map;

public interface Tax {

    Map<String, Money> calculate(Address address, List<TaxableLine> lines);

    record TaxableLine(String lineId, long taxClassId, Money taxable) {
    }
}
