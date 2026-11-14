package com.congthanh.subscriptionservice.adapter.out.persistence;

import com.congthanh.subscriptionservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
class UuidV7Ids implements Ids {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        long ms = System.currentTimeMillis();
        long msb = (ms << 16) | (0x7L << 12) | random.nextInt(1 << 12);
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }
}
