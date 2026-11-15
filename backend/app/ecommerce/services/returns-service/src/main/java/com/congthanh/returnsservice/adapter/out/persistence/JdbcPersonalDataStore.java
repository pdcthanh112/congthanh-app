package com.congthanh.returnsservice.adapter.out.persistence;

import com.congthanh.commonservice.id.ErasedIds;
import com.congthanh.ecommerce.privacy.v1.ErasureCompleted;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.returnsservice.application.port.out.PersonalDataStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

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
        String anon = ErasedIds.of(subjectId);
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

}
