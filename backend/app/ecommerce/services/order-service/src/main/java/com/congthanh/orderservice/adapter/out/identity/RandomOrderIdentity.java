package com.congthanh.orderservice.adapter.out.identity;

import com.congthanh.orderservice.application.port.out.OrderIdentity;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * id nội bộ: UUID. Mã đơn cho khách: CT-yyMMdd-XXXXXXXX (Crockford base32, 40 bit ngẫu nhiên),
 * không đoán được và không lộ số lượng đơn như id tăng dần.
 */
@Component
class RandomOrderIdentity implements OrderIdentity {

    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyMMdd");
    private final SecureRandom random = new SecureRandom();

    @Override
    public String newOrderId() {
        return UUID.randomUUID().toString();
    }

    @Override
    public String newOrderCode() {
        long bits = random.nextLong() & 0xFF_FFFF_FFFFL;
        char[] out = new char[8];
        for (int i = 7; i >= 0; i--) {
            out[i] = ALPHABET[(int) (bits & 31)];
            bits >>>= 5;
        }
        return "CT-" + LocalDate.now(ZoneOffset.UTC).format(DAY) + "-" + new String(out);
    }
}
