package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.ShippingOption;
import com.congthanh.checkoutservice.domain.model.Money;

import java.util.List;

/** Phí giao theo địa chỉ và kiện hàng (shipping-service). */
public interface Shipping {

    List<ShippingOption> quote(Address destination, int weightGrams, Money declaredValue);
}
