package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.ecommerce.privacy.v1.ErasureCompleted;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.returnsservice.application.port.out.PersonalDataStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
class JdbcPersonalDataStore implements PersonalDataStore {

    static final String PROCESSOR = "returns-service";
    static final String RETENTION = "Return and refund records are kept 7 years for accounting; the customer link was pseudonymised.";

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
                UPDATE return_requests SET customer_id = :anon, note = NULL WHERE customer_id = :subject""").param("anon", anon).param("subject", subjectId).update();
        jdbc.sql("""
                UPDATE order_snapshots SET customer_id = :anon WHERE customer_id = :subject""").param("anon", anon).param("subject", subjectId).update();
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
