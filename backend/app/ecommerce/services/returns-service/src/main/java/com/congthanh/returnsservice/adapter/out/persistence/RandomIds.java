package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.commonservice.id.Snowflake;
import com.congthanh.returnsservice.application.port.out.Ids;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

@Component
class RandomIds implements Ids {

    /** Bỏ 0/O/1/I/L để đọc qua điện thoại không nhầm. */
    private static final char[] RMA_ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private final SecureRandom random = new SecureRandom();

    @Override
    public String next() {
        return Snowflake.nextString();
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
