package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;

import java.util.function.Predicate;

/** Resilience4j chỉ retry / tính vào circuit breaker các lỗi tạm thời. */
public class TransientFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        return !(t instanceof CheckoutException e) || e.kind() == CheckoutException.Kind.UNAVAILABLE;
    }
}
