package com.congthanh.checkoutservice.adapter.out.persistence;

import com.congthanh.checkoutservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

/** UUIDv7 (RFC 9562): sắp theo thời gian, tốt cho chỉ mục B-tree. */
@Component
class UuidV7Ids implements Ids {

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    UuidV7Ids(Clock clock) {
        this.clock = clock;
    }

    @Override
    public String newId() {
        byte[] b = new byte[16];
        random.nextBytes(b);
        long ms = clock.millis();
        for (int i = 0; i < 6; i++) {
            b[i] = (byte) (ms >>> (40 - 8 * i));
        }
        b[6] = (byte) ((b[6] & 0x0F) | 0x70);
        b[8] = (byte) ((b[8] & 0x3F) | 0x80);
        long hi = 0;
        long lo = 0;
        for (int i = 0; i < 8; i++) {
            hi = (hi << 8) | (b[i] & 0xFF);
            lo = (lo << 8) | (b[i + 8] & 0xFF);
        }
        return new UUID(hi, lo).toString();
    }
}
