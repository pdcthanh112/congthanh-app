package com.congthanh.orderservice.adapter.out.grpc;

import com.congthanh.orderservice.domain.exception.OrderException;

import java.util.function.Predicate;

/**
 * Resilience4j chỉ retry và chỉ tính vào circuit breaker các lỗi tạm thời (dịch vụ không phản hồi),
 * không tính lỗi nghiệp vụ như "voucher hết hạn". Tham chiếu trong application.yaml.
 */
public class TransientFailure implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable t) {
        return t instanceof OrderException e && e.kind() == OrderException.Kind.UNAVAILABLE;
    }
}
