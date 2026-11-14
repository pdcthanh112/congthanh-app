package com.congthanh.ledgerservice.application.port.in;

import com.congthanh.ledgerservice.domain.posting.FinancialFact;

/** Hạch toán tự động từ event; idempotent theo event id. */
public interface LedgerPosting {

    /** @return id bút toán đã ghi, hoặc null nếu event này đã được hạch toán trước đó */
    String post(FinancialFact fact);

    /** Event không dịch được thành sự kiện tài chính: lưu lại để người xử lý, không đoán. */
    void recordUnposted(String eventId, String eventType, String reason);
}
