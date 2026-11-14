package com.congthanh.ledgerservice.adapter.out.persistence;

import com.congthanh.ledgerservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

/** UUIDv7 (RFC 9562): sắp theo thời gian nên index B-tree của bảng chỉ-ghi-thêm không bị phân mảnh. */
@Component
class UuidV7Ids implements Ids {

    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        long ms = System.currentTimeMillis();
        long randA = random.nextInt(1 << 12);
        long msb = (ms << 16) | (0x7L << 12) | randA;
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }
}
