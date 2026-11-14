package com.congthanh.returnsservice.application.port.out;

import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface ReturnRepository {

    /** Lưu (khoá lạc quan theo version) và ghi event vào outbox cùng transaction. */
    void save(ReturnRequest r);

    Optional<ReturnRequest> findById(String id);

    List<ReturnRequest> byCustomer(String customerId, int limit);

    List<ReturnRequest> byStatus(ReturnStatus status, int limit);

    List<ReturnRequest> stuck(ReturnStatus status, Instant updatedBefore, int limit);

    Map<String, Integer> openQuantities(String orderId);

    BigDecimal refundCommitted(String orderId, String exceptReturnId);
}
