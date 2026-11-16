package com.congthanh.ledgerservice.application.service;

import com.congthanh.ledgerservice.application.port.in.LedgerPosting;
import com.congthanh.ledgerservice.application.port.in.LedgerQueries;
import com.congthanh.ledgerservice.application.port.in.ManualEntries;
import com.congthanh.ledgerservice.application.port.out.Ids;
import com.congthanh.ledgerservice.application.port.out.LedgerRepository;
import com.congthanh.ledgerservice.domain.exception.LedgerException;
import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.AccountCode;
import com.congthanh.ledgerservice.domain.model.JournalEntry;
import com.congthanh.ledgerservice.domain.model.Posting;
import com.congthanh.ledgerservice.domain.posting.FinancialFact;
import com.congthanh.ledgerservice.domain.posting.PostingRules;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LedgerService implements LedgerPosting, LedgerQueries, ManualEntries {

    private static final int MAX_PAGE = 500;

    private final LedgerRepository repo;
    private final Ids ids;
    private final Clock clock;

    @Override
    @Transactional
    public String post(FinancialFact fact) {
        if (repo.existsBySourceEvent(fact.eventId())) {
            return null;
        }
        if (fact instanceof FinancialFact.ProviderFees f) {
            String expense = "expense:psp_fees:" + f.provider().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
            BigDecimal posted = repo.bySource("psp_fees", PostingRules.feeSource(f.provider(), f.businessDate(), f.currency())).stream()
                    .flatMap(e -> e.postings().stream())
                    .filter(p -> p.account().value().equals(expense))
                    .map(p -> p.side() == com.congthanh.ledgerservice.domain.model.Side.DEBIT ? p.amount() : p.amount().negate())
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (posted.compareTo(f.fees()) == 0) {
                return null;
            }
            fact = f.withAlreadyPosted(posted);
        }
        JournalEntry entry = PostingRules.post(fact, ids.next(), clock.instant());
        repo.append(entry);
        return entry.id();
    }

    @Override
    @Transactional
    public void recordUnposted(String eventId, String eventType, String reason) {
        repo.saveUnposted(eventId, eventType, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountBalance> balance(String account) {
        return repo.balances(AccountCode.of(account).value());
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntry> entriesBySource(String sourceType, String sourceId) {
        return repo.bySource(sourceType, sourceId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<JournalEntry> entriesByAccount(String account, LocalDate from, LocalDate to, int limit) {
        if (from != null && to != null && from.isAfter(to)) {
            throw LedgerException.invalid("from must not be after to");
        }
        return repo.byAccount(AccountCode.of(account).value(), from, to, Math.clamp(limit, 1, MAX_PAGE));
    }

    @Override
    @Transactional(readOnly = true)
    public TrialBalance trialBalance(LocalDate asOf) {
        LocalDate date = asOf != null ? asOf : LocalDate.now(clock.withZone(PostingRules.BOOKING_ZONE));
        List<AccountBalance> rows = repo.trialBalance(date);
        Map<String, BigDecimal> net = new HashMap<>();
        for (AccountBalance b : rows) {
            net.merge(b.currency(), b.debits().subtract(b.credits()), BigDecimal::add);
        }
        boolean balanced = net.values().stream().allMatch(v -> v.signum() == 0);
        return new TrialBalance(date, rows, balanced);
    }

    @Override
    @Transactional
    public ManualEntry propose(String description, LocalDate bookingDate, List<Posting> postings, String maker) {
        String id = ids.next();
        LocalDate date = bookingDate != null ? bookingDate : LocalDate.now(clock.withZone(PostingRules.BOOKING_ZONE));
        // Dựng thử để kiểm cân ngay lúc lập, không đợi tới lúc duyệt mới báo lỗi.
        new JournalEntry(id, "manual", id, null, description, date, postings, maker, clock.instant());
        ManualEntry m = new ManualEntry(id, description, date, postings, maker, "PENDING", null);
        repo.saveManual(m);
        return m;
    }

    @Override
    @Transactional
    public String approve(String manualEntryId, String checker) {
        ManualEntry m = pendingFor(manualEntryId, checker);
        String entryId = ids.next();
        JournalEntry entry = m.reversesEntryId() == null
                ? new JournalEntry(entryId, "manual", m.id(), null, m.description(), m.bookingDate(), m.postings(),
                m.maker() + "/" + checker, clock.instant())
                : repo.findEntry(m.reversesEntryId())
                .orElseThrow(() -> LedgerException.notFound("journal entry " + m.reversesEntryId()))
                .reversal(entryId, m.description(), m.maker() + "/" + checker, m.bookingDate(), clock.instant());
        repo.append(entry);
        repo.updateManualStatus(m.id(), "APPROVED", checker, entryId, null);
        return entryId;
    }

    @Override
    @Transactional
    public void reject(String manualEntryId, String checker, String reason) {
        ManualEntry m = pendingFor(manualEntryId, checker);
        repo.updateManualStatus(m.id(), "REJECTED", checker, null, reason);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ManualEntry> pending() {
        return repo.pendingManual();
    }

    @Override
    @Transactional
    public ManualEntry proposeReversal(String entryId, String reason, String maker) {
        JournalEntry original = repo.findEntry(entryId)
                .orElseThrow(() -> LedgerException.notFound("journal entry " + entryId));
        if (reason == null || reason.isBlank()) {
            throw LedgerException.invalid("a reversal needs a reason");
        }
        String id = ids.next();
        LocalDate date = LocalDate.now(clock.withZone(PostingRules.BOOKING_ZONE));
        List<Posting> flipped = original.reversal(id, reason, maker, date, clock.instant()).postings();
        ManualEntry m = new ManualEntry(id, reason, date, flipped, maker, "PENDING", entryId);
        repo.saveManual(m);
        return m;
    }

    private ManualEntry pendingFor(String id, String checker) {
        ManualEntry m = repo.lockManual(id).orElseThrow(() -> LedgerException.notFound("manual entry " + id));
        if (!"PENDING".equals(m.status())) {
            throw LedgerException.conflict("manual entry " + id + " is " + m.status());
        }
        if (m.maker().equals(checker)) {
            throw LedgerException.forbidden("the maker of a manual entry cannot approve or reject it");
        }
        return m;
    }
}
