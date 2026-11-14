package com.congthanh.ledgerservice.domain.model;

import com.congthanh.ledgerservice.domain.exception.LedgerException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Bút toán kép bất biến. Bất biến: ít nhất hai dòng, và với từng tiền tệ tổng Nợ = tổng Có.
 * Tạo ra rồi không sửa, không xoá; sai thì ghi bút toán đảo ({@link #reversal}).
 *
 * @param sourceEventId event_id của event nguồn (idempotency); null với bút toán tay
 */
public record JournalEntry(String id, String sourceType, String sourceId, String sourceEventId, String description,
                           LocalDate bookingDate, List<Posting> postings, String createdBy, Instant createdAt) {

    public JournalEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(bookingDate, "bookingDate");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sourceType == null || sourceType.isBlank() || sourceId == null || sourceId.isBlank()) {
            throw LedgerException.invalid("journal entry needs a source type and id");
        }
        postings = List.copyOf(Objects.requireNonNull(postings, "postings"));
        if (postings.size() < 2) {
            throw LedgerException.invalid("journal entry needs at least two postings");
        }
        Map<String, BigDecimal> net = new HashMap<>();
        for (Posting p : postings) {
            BigDecimal signed = p.side() == Side.DEBIT ? p.amount() : p.amount().negate();
            net.merge(p.currency(), signed, BigDecimal::add);
        }
        net.forEach((currency, diff) -> {
            if (diff.signum() != 0) {
                throw LedgerException.invalid("journal entry is unbalanced by " + diff + " " + currency);
            }
        });
    }

    /** Bút toán đảo: cùng tài khoản, đổi chiều Nợ/Có. */
    public JournalEntry reversal(String newId, String reason, String by, LocalDate date, Instant now) {
        List<Posting> flipped = postings.stream()
                .map(p -> new Posting(p.account(), p.side() == Side.DEBIT ? Side.CREDIT : Side.DEBIT, p.amount(),
                        p.currency()))
                .toList();
        return new JournalEntry(newId, "reversal", id, null, "Reversal of " + id + ": " + reason, date, flipped,
                by, now);
    }
}
