package com.congthanh.disputeservice.application.port.out;

import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DisputeRepository {

    /** Lưu (khoá lạc quan) và ghi event vào outbox cùng transaction. */
    void save(Dispute d);

    Optional<Dispute> findById(String id);

    Optional<Dispute> findByProvider(String provider, String providerDisputeId);

    List<Dispute> byStatus(DisputeStatus status, int limit);

    List<Dispute> openDueBefore(Instant before, int limit);
}
