package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.PaymentMethod;

public interface PaymentProviders {

    PaymentProvider forMethod(PaymentMethod method);

    PaymentProvider byName(String name);

    boolean supports(PaymentMethod method);
}
