package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.Address;

/** Sổ địa chỉ của khách (customer-service). */
public interface Customers {

    Address shippingAddress(String customerId, String addressId);
}
