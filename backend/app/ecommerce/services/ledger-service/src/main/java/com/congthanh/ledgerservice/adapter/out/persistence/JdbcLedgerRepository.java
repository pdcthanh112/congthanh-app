package com.congthanh.ledgerservice.adapter.out.persistence;

import com.congthanh.ecommerce.ledger.v1.JournalEntryPosted;
import com.congthanh.ledgerservice.application.port.in.ManualEntries.ManualEntry;
import com.congthanh.ledgerservice.application.port.out.LedgerRepository;
import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.AccountCode;
import com.congthanh.ledgerservice.domain.model.JournalEntry;
import com.congthanh.ledgerservice.domain.model.Posting;
import com.congthanh.ledgerservice.domain.model.Side;
import com.congthanh.platform.outbox.OutboxWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Postgres, chỉ INSERT vào journal_entries/postings (trigger chặn UPDATE/DELETE, xem 0001-ledger.sql).
 * Số dư nằm ở account_balances, cập nhật trong cùng transaction với bút toán.
 */
@Component
@RequiredArgsConstructor
class JdbcLedgerRepository implements LedgerRepository {

    static final String AGGREGATE = "ledger";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final ObjectMapper json;

    @Override
    public boolean existsBySourceEvent(String sourceEventId) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM journal_entries WHERE source_event_id = :id)")
                .param("id", sourceEventId).query(Boolean.class).single();
    }

    @Override
    public void append(JournalEntry e) {
        jdbc.sql("""
                        INSERT INTO journal_entries (id, source_type, source_id, source_event_id, description, booking_date,
                                                     created_by, created_at)
                        VALUES (:id::bigint, :sourceType, :sourceId, :sourceEventId, :description, :bookingDate, :createdBy, :createdAt)""")
                .param("id", e.id()).param("sourceType", e.sourceType()).param("sourceId", e.sourceId())
                .param("sourceEventId", e.sourceEventId()).param("description", e.description())
                .param("bookingDate", Date.valueOf(e.bookingDate())).param("createdBy", e.createdBy())
                .param("createdAt", Timestamp.from(e.createdAt()))
                .update();
        int line = 0;
        for (Posting p : e.postings()) {
            jdbc.sql("""
                            INSERT INTO accounts (code, type) VALUES (:code, :type) ON CONFLICT (code) DO NOTHING""")
                    .param("code", p.account().value()).param("type", p.account().type().name()).update();
            jdbc.sql("""
                            INSERT INTO postings (entry_id, line_no, account_code, side, amount, currency)
                            VALUES (:entryId::bigint, :line, :account, :side, :amount, :currency)""")
                    .param("entryId", e.id()).param("line", ++line).param("account", p.account().value())
                    .param("side", p.side().name()).param("amount", p.amount()).param("currency", p.currency())
                    .update();
            BigDecimal debit = p.side() == Side.DEBIT ? p.amount() : BigDecimal.ZERO;
            BigDecimal credit = p.side() == Side.CREDIT ? p.amount() : BigDecimal.ZERO;
            jdbc.sql("""
                            INSERT INTO account_balances (account_code, currency, debits, credits)
                            VALUES (:account, :currency, :debit, :credit)
                            ON CONFLICT (account_code, currency) DO UPDATE
                            SET debits = account_balances.debits + EXCLUDED.debits,
                                credits = account_balances.credits + EXCLUDED.credits""")
                    .param("account", p.account().value()).param("currency", p.currency())
                    .param("debit", debit).param("credit", credit).update();
        }
        outbox.write(AGGREGATE, e.id(), toProto(e));
    }

    @Override
    public Optional<JournalEntry> findEntry(String id) {
        return loadEntries("WHERE e.id = :p1::bigint", Map.of("p1", id), 1).stream().findFirst();
    }

    @Override
    public List<AccountBalance> balances(String account) {
        return jdbc.sql("SELECT account_code, currency, debits, credits FROM account_balances WHERE account_code = :a ORDER BY currency")
                .param("a", account)
                .query((rs, i) -> new AccountBalance(AccountCode.of(rs.getString(1)), rs.getString(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4)))
                .list();
    }

    @Override
    public List<JournalEntry> bySource(String sourceType, String sourceId) {
        return loadEntries("WHERE e.source_type = :p1 AND e.source_id = :p2", Map.of("p1", sourceType, "p2", sourceId), 200);
    }

    @Override
    public List<JournalEntry> byAccount(String account, LocalDate from, LocalDate to, int limit) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("p1", account);
        StringBuilder where = new StringBuilder("WHERE e.id IN (SELECT entry_id FROM postings WHERE account_code = :p1)");
        if (from != null) {
            where.append(" AND e.booking_date >= :p2");
            params.put("p2", Date.valueOf(from));
        }
        if (to != null) {
            where.append(" AND e.booking_date <= :p3");
            params.put("p3", Date.valueOf(to));
        }
        return loadEntries(where.toString(), params, limit);
    }

    @Override
    public List<AccountBalance> trialBalance(LocalDate asOf) {
        return jdbc.sql("""
                        SELECT p.account_code, p.currency,
                               COALESCE(SUM(p.amount) FILTER (WHERE p.side = 'DEBIT'), 0),
                               COALESCE(SUM(p.amount) FILTER (WHERE p.side = 'CREDIT'), 0)
                        FROM postings p JOIN journal_entries e ON e.id = p.entry_id
                        WHERE e.booking_date <= :asOf
                        GROUP BY p.account_code, p.currency
                        ORDER BY p.account_code, p.currency""")
                .param("asOf", Date.valueOf(asOf))
                .query((rs, i) -> new AccountBalance(AccountCode.of(rs.getString(1)), rs.getString(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4)))
                .list();
    }

    @Override
    public void saveUnposted(String eventId, String eventType, String reason) {
        jdbc.sql("""
                        INSERT INTO unposted_events (event_id, event_type, reason) VALUES (:id, :type, :reason)
                        ON CONFLICT (event_id) DO NOTHING""")
                .param("id", eventId).param("type", eventType).param("reason", reason).update();
    }

    @Override
    public void saveManual(ManualEntry m) {
        jdbc.sql("""
                        INSERT INTO manual_entries (id, description, booking_date, postings, maker, status, reverses_entry_id)
                        VALUES (:id::bigint, :description, :date, :postings::jsonb, :maker, :status, :reverses::bigint)""")
                .param("id", m.id()).param("description", m.description()).param("date", Date.valueOf(m.bookingDate()))
                .param("postings", json.writeValueAsString(m.postings().stream().map(PostingJson::of).toList()))
                .param("maker", m.maker()).param("status", m.status()).param("reverses", m.reversesEntryId())
                .update();
    }

    @Override
    public Optional<ManualEntry> lockManual(String id) {
        return jdbc.sql("""
                        SELECT id, description, booking_date, postings, maker, status, reverses_entry_id
                        FROM manual_entries WHERE id = :id::bigint FOR UPDATE""")
                .param("id", id).query((rs, i) -> manual(rs)).optional();
    }

    @Override
    public void updateManualStatus(String id, String status, String checker, String postedEntryId, String note) {
        jdbc.sql("""
                        UPDATE manual_entries SET status = :status, checker = :checker, posted_entry_id = :posted::bigint,
                               note = :note, decided_at = now()
                        WHERE id = :id::bigint""")
                .param("id", id).param("status", status).param("checker", checker).param("posted", postedEntryId)
                .param("note", note).update();
    }

    @Override
    public List<ManualEntry> pendingManual() {
        return jdbc.sql("""
                        SELECT id, description, booking_date, postings, maker, status, reverses_entry_id
                        FROM manual_entries WHERE status = 'PENDING' ORDER BY created_at""")
                .query((rs, i) -> manual(rs)).list();
    }

    private ManualEntry manual(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<PostingJson> raw = json.readValue(rs.getString("postings"), new TypeReference<>() {
        });
        return new ManualEntry(rs.getString("id"), rs.getString("description"),
                rs.getDate("booking_date").toLocalDate(), raw.stream().map(PostingJson::toDomain).toList(),
                rs.getString("maker"), rs.getString("status"), rs.getString("reverses_entry_id"));
    }

    private List<JournalEntry> loadEntries(String where, Map<String, ?> params, int limit) {
        var spec = jdbc.sql("""
                SELECT e.id, e.source_type, e.source_id, e.source_event_id, e.description, e.booking_date,
                       e.created_by, e.created_at
                FROM journal_entries e %s ORDER BY e.created_at DESC LIMIT %d""".formatted(where, limit));
        for (var p : params.entrySet()) {
            spec = spec.param(p.getKey(), p.getValue());
        }
        List<Object[]> heads = spec.query((rs, i) -> new Object[]{rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getString(5), rs.getDate(6).toLocalDate(), rs.getString(7),
                rs.getTimestamp(8).toInstant()}).list();
        List<JournalEntry> out = new ArrayList<>(heads.size());
        for (Object[] h : heads) {
            List<Posting> postings = jdbc.sql("""
                            SELECT account_code, side, amount, currency FROM postings WHERE entry_id = :id::bigint ORDER BY line_no""")
                    .param("id", h[0])
                    .query((rs, i) -> new Posting(AccountCode.of(rs.getString(1)), Side.valueOf(rs.getString(2)),
                            rs.getBigDecimal(3), rs.getString(4)))
                    .list();
            out.add(new JournalEntry((String) h[0], (String) h[1], (String) h[2], (String) h[3], (String) h[4],
                    (LocalDate) h[5], postings, (String) h[6], (java.time.Instant) h[7]));
        }
        return out;
    }

    static JournalEntryPosted toProto(JournalEntry e) {
        var b = JournalEntryPosted.newBuilder()
                .setEntryId(e.id()).setSourceType(e.sourceType()).setSourceId(e.sourceId())
                .setSourceEventId(e.sourceEventId() == null ? "" : e.sourceEventId())
                .setDescription(e.description() == null ? "" : e.description())
                .setBookingDate(e.bookingDate().toString());
        for (Posting p : e.postings()) {
            b.addPostings(com.congthanh.ecommerce.ledger.v1.Posting.newBuilder()
                    .setAccount(p.account().value()).setSide(p.side().name()).setAmount(money(p.amount(), p.currency())));
        }
        return b.build();
    }

    static com.congthanh.ecommerce.common.v1.Money money(BigDecimal amount, String currency) {
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return com.congthanh.ecommerce.common.v1.Money.newBuilder().setCurrencyCode(currency)
                .setUnits(units.longValueExact()).setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }

    /** Dạng JSON của một dòng trong bút toán tay đang chờ duyệt. */
    record PostingJson(String account, String side, BigDecimal amount, String currency) {
        static PostingJson of(Posting p) {
            return new PostingJson(p.account().value(), p.side().name(), p.amount(), p.currency());
        }

        Posting toDomain() {
            return new Posting(AccountCode.of(account), Side.valueOf(side), amount, currency);
        }
    }
}
