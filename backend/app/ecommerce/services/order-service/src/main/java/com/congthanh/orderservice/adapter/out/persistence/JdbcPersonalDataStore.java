package com.congthanh.orderservice.adapter.out.persistence;

import com.congthanh.ecommerce.privacy.v1.ErasureCompleted;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.orderservice.application.port.out.PersonalDataStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
class JdbcPersonalDataStore implements PersonalDataStore {

    static final String PROCESSOR = "order-service";
    static final String RETENTION = "Order records (amounts, tax, items) are kept 7 years for tax and accounting (IRS); name, phone, addresses, IP and payment token were removed.";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;

    JdbcPersonalDataStore(JdbcClient jdbc, OutboxWriter outbox) {
        this.jdbc = jdbc;
        this.outbox = outbox;
    }

    @Override
    public String anonymize(String subjectId) {
        String anon = erasedId(subjectId);
        jdbc.sql("""
                UPDATE orders SET ship_full_name = 'Erased', ship_phone = NULL, ship_line1 = 'Erased', ship_line2 = NULL,
                       ship_ward = NULL, ship_district = NULL, ship_postal_code = NULL,
                       bill_full_name = NULL, bill_phone = NULL, bill_line1 = NULL, bill_line2 = NULL, bill_ward = NULL,
                       bill_district = NULL, bill_postal_code = NULL, client_ip = NULL, payment_token = NULL,
                       return_url = NULL, note = NULL, customer_id = :anon
                WHERE customer_id = :subject""").param("anon", anon).param("subject", subjectId).update();
        return RETENTION;
    }

    @Override
    public void confirmErasure(String requestId, String subjectId, String retentionReason) {
        outbox.write("privacy", subjectId, ErasureCompleted.newBuilder()
                .setRequestId(requestId).setSubjectId(subjectId).setProcessor(PROCESSOR)
                .setPartiallyRetained(retentionReason != null)
                .setRetentionReason(retentionReason == null ? "" : retentionReason)
                .build());
    }

    /** Định danh thay thế ổn định, không đảo ngược (cùng công thức với libs/ts/platform/privacy). */
    static String erasedId(String subjectId) {
        try {
            byte[] h = MessageDigest.getInstance("SHA-256").digest(subjectId.getBytes(StandardCharsets.UTF_8));
            return "erased-" + HexFormat.of().formatHex(h).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
