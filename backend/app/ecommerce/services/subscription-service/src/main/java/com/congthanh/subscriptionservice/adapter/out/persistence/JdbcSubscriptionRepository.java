package com.congthanh.subscriptionservice.adapter.out.persistence;

import com.congthanh.ecommerce.subscription.v1.RenewalFailed;
import com.congthanh.ecommerce.subscription.v1.SubscriptionCreated;
import com.congthanh.ecommerce.subscription.v1.SubscriptionLine;
import com.congthanh.ecommerce.subscription.v1.SubscriptionOrderPlaced;
import com.congthanh.ecommerce.subscription.v1.SubscriptionRenewalUpcoming;
import com.congthanh.ecommerce.subscription.v1.SubscriptionStatusChanged;
import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.subscriptionservice.application.port.out.SubscriptionRepository;
import com.congthanh.subscriptionservice.domain.event.SubscriptionEvent;
import com.congthanh.subscriptionservice.domain.model.Line;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;
import com.google.protobuf.Message;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
class JdbcSubscriptionRepository implements SubscriptionRepository {

    static final String AGGREGATE = "subscription";
    private static final String COLUMNS = """
            id, customer_id, lines, interval_days, next_delivery_date, payment_method_ref, address_id, status, cycle,
            attempts, next_attempt_at, reminded_cycle, paused_until, cancel_reason, created_at, version""";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final ObjectMapper json;

    @Override
    public void save(Subscription s) {
        String lines = json.writeValueAsString(s.lines());
        int n;
        if (s.version() == 0) {
            n = jdbc.sql("""
                            INSERT INTO subscriptions (%s) VALUES (:id::bigint, :c, :lines::jsonb, :interval, :next, :pm, :addr,
                                :status, :cycle, :attempts, :nextAttempt, :reminded, :pausedUntil, :cancel, :createdAt, 1)"""
                            .formatted(COLUMNS))
                    .param("id", s.id()).param("c", s.customerId()).param("lines", lines)
                    .param("interval", s.intervalDays()).param("next", Date.valueOf(s.nextDeliveryDate()))
                    .param("pm", s.paymentMethodRef()).param("addr", s.addressId()).param("status", s.status().name())
                    .param("cycle", s.cycle()).param("attempts", s.attempts()).param("nextAttempt", ts(s.nextAttemptAt()))
                    .param("reminded", s.remindedCycle()).param("pausedUntil", date(s.pausedUntil()))
                    .param("cancel", s.cancelReason()).param("createdAt", ts(s.createdAt()))
                    .update();
        } else {
            n = jdbc.sql("""
                            UPDATE subscriptions SET lines = :lines::jsonb, interval_days = :interval, next_delivery_date = :next,
                                status = :status, cycle = :cycle, attempts = :attempts, next_attempt_at = :nextAttempt,
                                reminded_cycle = :reminded, paused_until = :pausedUntil, cancel_reason = :cancel,
                                updated_at = now(), version = version + 1
                            WHERE id = :id::bigint AND version = :version""")
                    .param("id", s.id()).param("lines", lines).param("interval", s.intervalDays())
                    .param("next", Date.valueOf(s.nextDeliveryDate())).param("status", s.status().name())
                    .param("cycle", s.cycle()).param("attempts", s.attempts()).param("nextAttempt", ts(s.nextAttemptAt()))
                    .param("reminded", s.remindedCycle()).param("pausedUntil", date(s.pausedUntil()))
                    .param("cancel", s.cancelReason()).param("version", s.version())
                    .update();
        }
        if (n != 1) {
            throw new OptimisticLockingFailureException("subscription " + s.id() + " was modified concurrently");
        }
        for (SubscriptionEvent e : s.pullEvents()) {
            outbox.write(AGGREGATE, s.id(), toProto(e));
        }
    }

    @Override
    public Optional<Subscription> findById(String id) {
        try {
            Long.parseLong(id);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        return jdbc.sql("SELECT " + COLUMNS + " FROM subscriptions WHERE id = :id::bigint").param("id", id)
                .query((rs, i) -> map(rs)).optional();
    }

    @Override
    public List<Subscription> byCustomer(String customerId) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM subscriptions WHERE customer_id = :c ORDER BY created_at DESC")
                .param("c", customerId).query((rs, i) -> map(rs)).list();
    }

    @Override
    public List<Subscription> byStatus(SubscriptionStatus status, int limit) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM subscriptions WHERE status = :s ORDER BY next_delivery_date LIMIT :limit")
                .param("s", status.name()).param("limit", limit).query((rs, i) -> map(rs)).list();
    }

    @Override
    public List<Subscription> claimDue(LocalDate horizon, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT %s FROM subscriptions
                        WHERE (status = 'ACTIVE' AND next_delivery_date <= :horizon)
                           OR (status = 'PAST_DUE' AND next_attempt_at <= :now)
                           OR (status = 'PAUSED' AND paused_until IS NOT NULL AND paused_until <= :horizon)
                        ORDER BY next_delivery_date LIMIT :limit FOR UPDATE SKIP LOCKED""".formatted(COLUMNS))
                .param("horizon", Date.valueOf(horizon)).param("now", ts(now)).param("limit", limit)
                .query((rs, i) -> map(rs)).list();
    }

    @Override
    public void recordRenewalOrder(String orderId, String subscriptionId, int cycle) {
        jdbc.sql("INSERT INTO renewal_orders (order_id, subscription_id, cycle) VALUES (:o, :s::bigint, :c) ON CONFLICT DO NOTHING")
                .param("o", orderId).param("s", subscriptionId).param("c", cycle).update();
    }

    @Override
    public Optional<RenewalOrder> renewalOrder(String orderId) {
        return jdbc.sql("SELECT order_id, subscription_id::text, cycle FROM renewal_orders WHERE order_id = :o")
                .param("o", orderId).query((rs, i) -> new RenewalOrder(rs.getString(1), rs.getString(2), rs.getInt(3)))
                .optional();
    }

    private Subscription map(ResultSet rs) throws SQLException {
        List<Line> lines = json.readValue(rs.getString("lines"), new TypeReference<List<Line>>() {
        });
        Timestamp nextAttempt = rs.getTimestamp("next_attempt_at");
        Date paused = rs.getDate("paused_until");
        return Subscription.rehydrate(rs.getString("id"), rs.getString("customer_id"), lines, rs.getInt("interval_days"),
                rs.getDate("next_delivery_date").toLocalDate(), rs.getString("payment_method_ref"),
                rs.getString("address_id"), SubscriptionStatus.valueOf(rs.getString("status")), rs.getInt("cycle"),
                rs.getInt("attempts"), nextAttempt == null ? null : nextAttempt.toInstant(), rs.getInt("reminded_cycle"),
                paused == null ? null : paused.toLocalDate(), rs.getString("cancel_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getLong("version"));
    }

    static Message toProto(SubscriptionEvent e) {
        Subscription s = e.subscription();
        return switch (e) {
            case SubscriptionEvent.Created c -> SubscriptionCreated.newBuilder().setSubscriptionId(s.id())
                    .setCustomerId(s.customerId()).setIntervalDays(s.intervalDays())
                    .setNextDeliveryDate(s.nextDeliveryDate().toString())
                    .addAllLines(s.lines().stream().map(l -> SubscriptionLine.newBuilder().setProductId(l.productId())
                            .setQuantity(l.quantity()).build()).toList())
                    .build();
            case SubscriptionEvent.RenewalUpcoming u -> SubscriptionRenewalUpcoming.newBuilder().setSubscriptionId(s.id())
                    .setCustomerId(s.customerId()).setNextDeliveryDate(s.nextDeliveryDate().toString()).setCycle(s.cycle())
                    .build();
            case SubscriptionEvent.OrderPlaced o -> SubscriptionOrderPlaced.newBuilder().setSubscriptionId(s.id())
                    .setCustomerId(s.customerId()).setCycle(o.cycle()).setOrderId(o.orderId()).build();
            case SubscriptionEvent.RenewalFailed f -> RenewalFailed.newBuilder().setSubscriptionId(s.id())
                    .setCustomerId(s.customerId()).setCycle(f.cycle()).setAttempt(f.attempt()).setReason(f.reason())
                    .setNextAttemptAt(f.nextAttemptAt() == null ? "" : f.nextAttemptAt().toString()).build();
            case SubscriptionEvent.StatusChanged c -> SubscriptionStatusChanged.newBuilder().setSubscriptionId(s.id())
                    .setCustomerId(s.customerId()).setFromStatus(c.from().name()).setToStatus(c.to().name())
                    .setReason(c.reason() == null ? "" : c.reason()).build();
        };
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Date date(LocalDate d) {
        return d == null ? null : Date.valueOf(d);
    }
}
