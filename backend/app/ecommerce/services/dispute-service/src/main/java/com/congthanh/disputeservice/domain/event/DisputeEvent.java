package com.congthanh.disputeservice.domain.event;

import com.congthanh.disputeservice.domain.model.Dispute;

public sealed interface DisputeEvent {

    Dispute dispute();

    record Opened(Dispute dispute) implements DisputeEvent {
    }

    record EvidenceSubmitted(Dispute dispute, String by) implements DisputeEvent {
    }

    record Closed(Dispute dispute) implements DisputeEvent {
    }
}
