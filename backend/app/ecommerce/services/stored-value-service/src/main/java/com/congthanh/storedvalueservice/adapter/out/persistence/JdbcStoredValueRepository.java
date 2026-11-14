package com.congthanh.storedvalueservice.adapter.out.persistence;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueCredited;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueDebited;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueHeld;
import com.congthanh.ecommerce.storedvalue.v1.StoredValueReleased;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.storedvalueservice.application.port.out.StoredValueRepository;
import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Hold;
import com.congthanh.storedvalueservice.domain.model.Movement;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcStoredValueRepository implements StoredValueRepository {

    static final String AGGREGATE = "storedvalue";
    private static final String ACCOUNT_COLUMNS = "id, customer_id, kind, currency, balance, held, status, created_at, version";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;

    @Override
    public Optional<Account> lockAccount(String customerId, Account.Kind kind, String currency) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE customer_id = :c AND kind = :k AND currency = :cur FOR UPDATE")
                .param("c", customerId).param("k", kind.name()).param("cur", currency)
                .query((rs, i) -> account(rs)).optional();
    }

    @Override
    public Optional<Account> lockAccountById(String accountId) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE id = :id::uuid FOR UPDATE")
                .param("id", accountId).query((rs, i) -> account(rs)).optional();
    }

    @Override
    public List<Account> byCustomer(String customerId) {
        return jdbc.sql("SELECT " + ACCOUNT_COLUMNS + " FROM accounts WHERE customer_id = :c ORDER BY created_at")
                .param("c", customerId).query((rs, i) -> account(rs)).list();
    }

    @Override
    public void save(Account a) {
        int n;
        if (a.version() == 0) {
            n = jdbc.sql("""
                            INSERT INTO accounts (id, customer_id, kind, currency, balance, held, status, created_at, version)
                            VALUES (:id::uuid, :c, :k, :cur, :balance, :held, :status, :createdAt, 1)
                            ON CONFLICT (id) DO NOTHING""")
                    .param("id", a.id()).param("c", a.customerId()).param("k", a.kind().name())
                    .param("cur", a.currency()).param("balance", a.balance()).param("held", a.held())
                    .param("status", a.status().name()).param("createdAt", Timestamp.from(a.createdAt()))
                    .update();
        } else {
            n = jdbc.sql("""
                            UPDATE accounts SET balance = :balance, held = :held, status = :status, version = version + 1
                            WHERE id = :id::uuid AND version = :version""")
                    .param("id", a.id()).param("balance", a.balance()).param("held", a.held())
                    .param("status", a.status().name()).param("version", a.version())
                    .update();
        }
        if (n != 1) {
            throw new OptimisticLockingFailureException("account " + a.id() + " was modified concurrently");
        }
        for (Movement m : a.pullMovements()) {
            jdbc.sql("""
                            INSERT INTO movements (account_id, type, amount, currency, reason, reference, order_id, balance_after, at)
                            VALUES (:a::uuid, :type, :amount, :cur, :reason, :ref, :orderId, :after, :at)""")
                    .param("a", m.accountId()).param("type", m.type().name()).param("amount", m.amount())
                    .param("cur", m.currency()).param("reason", m.reason()).param("ref", m.reference())
                    .param("orderId", m.orderId()).param("after", m.balanceAfter()).param("at", Timestamp.from(m.at()))
                    .update();
            outbox.write(AGGREGATE, m.accountId(), toProto(m));
        }
    }

    @Override
    public Optional<Movement> findMovement(Movement.Type type, String reference) {
        return jdbc.sql("""
                        SELECT m.account_id, a.customer_id, a.kind, m.type, m.amount, m.currency, m.reason, m.reference,
                               m.order_id, m.balance_after, m.at
                        FROM movements m JOIN accounts a ON a.id = m.account_id
                        WHERE m.type = :type AND m.reference = :ref""")
                .param("type", type.name()).param("ref", reference).query((rs, i) -> movement(rs)).optional();
    }

    @Override
    public List<Movement> history(String accountId, int limit) {
        return jdbc.sql("""
                        SELECT m.account_id, a.customer_id, a.kind, m.type, m.amount, m.currency, m.reason, m.reference,
                               m.order_id, m.balance_after, m.at
                        FROM movements m JOIN accounts a ON a.id = m.account_id
                        WHERE m.account_id = :id::uuid ORDER BY m.id DESC LIMIT :limit""")
                .param("id", accountId).param("limit", limit).query((rs, i) -> movement(rs)).list();
    }

    @Override
    public Optional<Hold> lockHold(String holdId) {
        return jdbc.sql("SELECT id, account_id, order_id, amount, status, expires_at FROM holds WHERE id = :id FOR UPDATE")
                .param("id", holdId).query((rs, i) -> hold(rs)).optional();
    }

    @Override
    public void saveHold(Hold h) {
        jdbc.sql("""
                        INSERT INTO holds (id, account_id, order_id, amount, status, expires_at)
                        VALUES (:id, :a::uuid, :orderId, :amount, :status, :expires)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status""")
                .param("id", h.id()).param("a", h.accountId()).param("orderId", h.orderId()).param("amount", h.amount())
                .param("status", h.status().name()).param("expires", Timestamp.from(h.expiresAt()))
                .update();
    }

    @Override
    public List<Hold> expiredHolds(Instant now, int limit) {
        return jdbc.sql("SELECT id, account_id, order_id, amount, status, expires_at FROM holds WHERE status = 'HELD' AND expires_at < :now ORDER BY expires_at LIMIT :limit")
                .param("now", Timestamp.from(now)).param("limit", limit).query((rs, i) -> hold(rs)).list();
    }

    private static Account account(ResultSet rs) throws SQLException {
        return Account.rehydrate(rs.getString("id"), rs.getString("customer_id"), Account.Kind.valueOf(rs.getString("kind")),
                rs.getString("currency"), rs.getBigDecimal("balance"), rs.getBigDecimal("held"),
                Account.Status.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant(),
                rs.getLong("version"));
    }

    private static Movement movement(ResultSet rs) throws SQLException {
        return new Movement(rs.getString(1), rs.getString(2), Account.Kind.valueOf(rs.getString(3)),
                Movement.Type.valueOf(rs.getString(4)), rs.getBigDecimal(5), rs.getString(6), rs.getString(7),
                rs.getString(8), rs.getString(9), rs.getBigDecimal(10), rs.getTimestamp(11).toInstant());
    }

    private static Hold hold(ResultSet rs) throws SQLException {
        return new Hold(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBigDecimal(4),
                Hold.Status.valueOf(rs.getString(5)), rs.getTimestamp(6).toInstant());
    }

    static Message toProto(Movement m) {
        Money amount = money(m.amount(), m.currency());
        Money after = money(m.balanceAfter(), m.currency());
        return switch (m.type()) {
            case CREDIT -> StoredValueCredited.newBuilder().setAccountId(m.accountId()).setCustomerId(m.customerId())
                    .setKind(m.kind().name()).setAmount(amount).setReason(nz(m.reason())).setReference(m.reference())
                    .setBalanceAfter(after).build();
            case HOLD -> StoredValueHeld.newBuilder().setAccountId(m.accountId()).setHoldId(m.reference())
                    .setOrderId(nz(m.orderId())).setAmount(amount).build();
            case DEBIT -> StoredValueDebited.newBuilder().setAccountId(m.accountId()).setCustomerId(m.customerId())
                    .setKind(m.kind().name()).setHoldId(m.reference()).setOrderId(nz(m.orderId())).setAmount(amount)
                    .setBalanceAfter(after).build();
            case RELEASE -> StoredValueReleased.newBuilder().setAccountId(m.accountId()).setHoldId(m.reference())
                    .setOrderId(nz(m.orderId())).setAmount(amount).setReason(nz(m.reason())).build();
        };
    }

    static Money money(BigDecimal amount, String currency) {
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode(currency).setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
