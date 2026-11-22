package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Address;

public interface Customers {

    Address shippingAddress(String customerId, String addressId);
}
