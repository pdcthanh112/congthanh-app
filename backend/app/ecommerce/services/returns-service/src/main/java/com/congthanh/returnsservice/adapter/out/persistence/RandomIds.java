package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.returnsservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.UUID;

@Component
class RandomIds implements Ids {

    /** Bỏ 0/O/1/I/L để đọc qua điện thoại không nhầm. */
    private static final char[] RMA_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        long ms = System.currentTimeMillis();
        long msb = (ms << 16) | (0x7L << 12) | random.nextInt(1 << 12);
        long lsb = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(msb, lsb).toString();
    }

    @Override
    public String rmaCode() {
        StringBuilder b = new StringBuilder("RMA-");
        for (int i = 0; i < 8; i++) {
            b.append(RMA_ALPHABET[random.nextInt(RMA_ALPHABET.length)]);
        }
        return b.toString();
    }
}
