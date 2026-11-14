package com.congthanh.disputeservice.application.port.out;

import com.congthanh.disputeservice.domain.model.Dispute;

import java.util.Map;

/** Gửi quyết định sang cổng thanh toán. */
public interface ProviderDisputes {

    void submitEvidence(Dispute dispute, Map<String, String> fields);

    void accept(Dispute dispute);
}
