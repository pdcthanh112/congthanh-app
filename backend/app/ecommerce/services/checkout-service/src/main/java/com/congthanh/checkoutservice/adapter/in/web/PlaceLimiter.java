package com.congthanh.checkoutservice.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

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
