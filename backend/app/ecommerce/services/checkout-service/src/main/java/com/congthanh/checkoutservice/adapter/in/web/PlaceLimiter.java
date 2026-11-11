package com.congthanh.checkoutservice.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Bulkhead cho "đặt đơn": mỗi pod chỉ xử lý đồng thời một số lượt đặt nhất định. Virtual thread nhận bao nhiêu
 * request cũng được, nhưng mỗi lượt đặt giữ kết nối DB và gọi 6 service khác; để tất cả chen vào cùng lúc thì ai
 * cũng chậm rồi cùng timeout. Quá ngưỡng thì trả 503 + Retry-After ngay (client thử lại với cùng Idempotency-Key),
 * phần còn lại vẫn nhanh và HPA có thời gian thêm pod.
 */
@Component
class PlaceLimiter {

    static final class Busy extends RuntimeException {
        Busy() {
            super("checkout is busy, please retry", null, false, false);
        }
    }

    private final Semaphore permits;
    private final Duration maxWait;

    PlaceLimiter(@Value("${checkout.place.max-concurrent:16}") int maxConcurrent,
                 @Value("${checkout.place.max-wait:PT0.2S}") Duration maxWait) {
        this.permits = new Semaphore(maxConcurrent, true);
        this.maxWait = maxWait;
    }

    <T> T run(Supplier<T> action) {
        boolean acquired;
        try {
            acquired = permits.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Busy();
        }
        if (!acquired) {
            throw new Busy();
        }
        try {
            return action.get();
        } finally {
            permits.release();
        }
    }
}
