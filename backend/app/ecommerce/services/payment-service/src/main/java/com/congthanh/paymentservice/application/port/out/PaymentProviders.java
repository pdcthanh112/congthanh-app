package com.congthanh.paymentservice.application.port.out;

import com.congthanh.paymentservice.domain.model.PaymentMethod;

public interface PaymentProviders {

    /** Cổng phụ trách phương thức này; PaymentException UNSUPPORTED nếu không có. */
    PaymentProvider forMethod(PaymentMethod method);

    PaymentProvider byName(String name);

    /** Có cổng nào (kể cả sandbox) phụ trách phương thức này không. */
    boolean supports(PaymentMethod method);
}
