package com.congthanh.ledgerservice.application.port.out;

import com.congthanh.ledgerservice.application.port.in.ManualEntries.ManualEntry;
import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.JournalEntry;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface LedgerRepository {

    boolean existsBySourceEvent(String sourceEventId);

    /**
     * Ghi bút toán (chỉ INSERT), cập nhật số dư và ghi JournalEntryPosted vào outbox trong cùng transaction.
     * Trùng source_event_id ném DataIntegrityViolation (hai consumer chạy song song).
     */
    void append(JournalEntry entry);

    Optional<JournalEntry> findEntry(String id);

    List<AccountBalance> balances(String account);

    List<JournalEntry> bySource(String sourceType, String sourceId);

    List<JournalEntry> byAccount(String account, LocalDate from, LocalDate to, int limit);

    /** Tổng hợp từ postings (không dùng bảng số dư) tới hết ngày asOf. */
    List<AccountBalance> trialBalance(LocalDate asOf);

    void saveUnposted(String eventId, String eventType, String reason);

    void saveManual(ManualEntry entry);

    Optional<ManualEntry> lockManual(String id);

    void updateManualStatus(String id, String status, String checker, String postedEntryId, String note);

    List<ManualEntry> pendingManual();
}
