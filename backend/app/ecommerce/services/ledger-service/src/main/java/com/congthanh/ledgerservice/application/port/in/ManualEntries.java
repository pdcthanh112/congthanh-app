package com.congthanh.ledgerservice.application.port.in;

import com.congthanh.ledgerservice.domain.model.Posting;

import java.time.LocalDate;
import java.util.List;

/**
 * Bút toán điều chỉnh tay của kế toán, hai người: một người lập (maker), một người KHÁC duyệt (checker).
 * Chỉ khi duyệt bút toán mới vào sổ.
 */
public interface ManualEntries {

    ManualEntry propose(String description, LocalDate bookingDate, List<Posting> postings, String maker);

    /** @return id bút toán đã vào sổ */
    String approve(String manualEntryId, String checker);

    void reject(String manualEntryId, String checker, String reason);

    List<ManualEntry> pending();

    /** Đảo một bút toán đã vào sổ (cũng qua maker/checker). */
    ManualEntry proposeReversal(String entryId, String reason, String maker);

    record ManualEntry(String id, String description, LocalDate bookingDate, List<Posting> postings, String maker,
                       String status, String reversesEntryId) {
    }
}
