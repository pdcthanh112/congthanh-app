package com.congthanh.returnsservice.adapter.in.messaging;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.privacy.v1.ErasureRequested;
import com.congthanh.platform.inbox.EventEnvelopes;
import com.congthanh.platform.inbox.ProcessedEvents;
import com.congthanh.returnsservice.application.port.in.PersonalDataErasure;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Yêu cầu xoá dữ liệu của privacy-service (ecommerce.privacy.events). */
@Component
class PrivacyEventsConsumer {

    static final String CONSUMER = "returns-service-privacy";

    private final PersonalDataErasure erasure;
    private final ProcessedEvents processed;
    private final TransactionTemplate tx;

    PrivacyEventsConsumer(PersonalDataErasure erasure, ProcessedEvents processed, TransactionTemplate tx) {
        this.erasure = erasure;
        this.processed = processed;
        this.tx = tx;
    }

    @KafkaListener(topics = "ecommerce.privacy.events", groupId = CONSUMER)
    void onPrivacyEvent(ConsumerRecord<String, byte[]> record) {
        EventEnvelope env = EventEnvelopes.parse(record.value());
        EventEnvelopes.unpack(env, ErasureRequested.class).ifPresent(e -> tx.executeWithoutResult(s -> {
            if (processed.markProcessed(env.getId(), CONSUMER)) {
                erasure.erase(e.getRequestId(), e.getSubjectId());
            }
        }));
    }
}
