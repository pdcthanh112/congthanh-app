package com.congthanh.loyaltyservice.adapter.out.persistence;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.loyalty.v1.PointsConfirmed;
import com.congthanh.ecommerce.loyalty.v1.PointsEarned;
import com.congthanh.ecommerce.loyalty.v1.PointsExpired;
import com.congthanh.ecommerce.loyalty.v1.PointsRedeemed;
import com.congthanh.ecommerce.loyalty.v1.PointsReversed;
import com.congthanh.ecommerce.loyalty.v1.TierChanged;
import com.congthanh.loyaltyservice.application.port.out.LoyaltyRepository;
import com.congthanh.loyaltyservice.domain.event.LoyaltyEvent;
import com.congthanh.loyaltyservice.domain.model.Lot;
import com.congthanh.loyaltyservice.domain.model.Member;
import com.congthanh.loyaltyservice.domain.model.Tier;
import com.congthanh.platform.outbox.OutboxWriter;
import com.google.protobuf.Message;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class JdbcLoyaltyRepository implements LoyaltyRepository {

    static final String AGGREGATE = "loyalty";
    private static final String LOT_COLUMNS = "id, customer_id, order_id, points, remaining, status, order_total, earned_at, confirm_at, expires_at";
    private static final String ACTIVE_LOTS = """
            customer_id = :c AND (status IN ('PENDING', 'AVAILABLE') OR earned_at > now() - interval '400 days')""";

    private final JdbcClient jdbc;
    private final OutboxWriter outbox;
    private final ObjectMapper json;
    private final Member.Rules rules;

    JdbcLoyaltyRepository(JdbcClient jdbc, OutboxWriter outbox, ObjectMapper json, Member.Rules rules) {
        this.jdbc = jdbc;
        this.outbox = outbox;
        this.json = json;
        this.rules = rules;
    }

    @Override
    public Member lock(String customerId) {
        jdbc.sql("INSERT INTO members (customer_id, tier) VALUES (:c, 'MEMBER') ON CONFLICT DO NOTHING").param("c", customerId).update();
        String tier = jdbc.sql("SELECT tier FROM members WHERE customer_id = :c FOR UPDATE").param("c", customerId)
                .query(String.class).single();
        return new Member(customerId, Tier.valueOf(tier), lots(customerId), 1);
    }

    @Override
    public Optional<Member> find(String customerId) {
        return jdbc.sql("SELECT tier FROM members WHERE customer_id = :c").param("c", customerId).query(String.class)
                .optional().map(t -> new Member(customerId, Tier.valueOf(t), lots(customerId), 1));
    }

    private List<Lot> lots(String customerId) {
        return jdbc.sql("SELECT " + LOT_COLUMNS + " FROM lots WHERE " + ACTIVE_LOTS + " ORDER BY earned_at")
                .param("c", customerId).query((rs, i) -> lot(rs)).list();
    }

    @Override
    public void save(Member m) {
        jdbc.sql("UPDATE members SET tier = :t, updated_at = now() WHERE customer_id = :c")
                .param("t", m.tier().name()).param("c", m.customerId()).update();
        for (Lot l : m.lots()) {
            jdbc.sql("""
                            INSERT INTO lots (%s) VALUES (:id::uuid, :c, :o, :points, :remaining, :status, :total, :earned, :confirm, :expires)
                            ON CONFLICT (id) DO UPDATE SET remaining = EXCLUDED.remaining, status = EXCLUDED.status,
                                confirm_at = EXCLUDED.confirm_at, expires_at = EXCLUDED.expires_at""".formatted(LOT_COLUMNS))
                    .param("id", l.id()).param("c", l.customerId()).param("o", l.orderId()).param("points", l.points())
                    .param("remaining", l.remaining()).param("status", l.status().name()).param("total", l.orderTotal())
                    .param("earned", ts(l.earnedAt())).param("confirm", ts(l.confirmAt())).param("expires", ts(l.expiresAt()))
                    .update();
        }
        for (LoyaltyEvent e : m.pullEvents()) {
            outbox.write(AGGREGATE, m.customerId(), toProto(e));
        }
    }

    @Override
    public Optional<String> customerOfOrder(String orderId) {
        return jdbc.sql("SELECT customer_id FROM lots WHERE order_id = :o LIMIT 1").param("o", orderId).query(String.class).optional();
    }

    @Override
    public List<String> dueForHousekeeping(Instant now, int limit) {
        return jdbc.sql("""
                        SELECT DISTINCT customer_id FROM lots
                        WHERE (status = 'PENDING' AND remaining > 0 AND COALESCE(confirm_at, earned_at + interval '45 days') <= :now)
                           OR (status = 'AVAILABLE' AND remaining > 0 AND expires_at < :now)
                        UNION
                        SELECT customer_id FROM members WHERE housed_at IS NULL OR housed_at < :now - interval '1 day'
                        LIMIT :limit""")
                .param("now", ts(now)).param("limit", limit).query(String.class).list();
    }

    @Override
    public void markHoused(String customerId, Instant now) {
        jdbc.sql("UPDATE members SET housed_at = :now WHERE customer_id = :c").param("now", ts(now)).param("c", customerId).update();
    }

    @Override
    public void saveRedemption(Redemption r) {
        jdbc.sql("""
                        INSERT INTO redemptions (id, customer_id, order_id, points, status, allocations)
                        VALUES (:id, :c, :o, :p, :s, :a::jsonb)
                        ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status, updated_at = now()""")
                .param("id", r.id()).param("c", r.customerId()).param("o", r.orderId()).param("p", r.points())
                .param("s", r.status()).param("a", json.writeValueAsString(r.allocations())).update();
    }

    @Override
    public Optional<Redemption> lockRedemption(String redemptionId) {
        return jdbc.sql("SELECT id, customer_id, order_id, points, status, allocations FROM redemptions WHERE id = :id FOR UPDATE")
                .param("id", redemptionId)
                .query((rs, i) -> new Redemption(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4),
                        rs.getString(5), json.readValue(rs.getString(6), new TypeReference<List<Member.Allocation>>() {
                })))
                .optional();
    }

    private static Lot lot(ResultSet rs) throws SQLException {
        return new Lot(rs.getString("id"), rs.getString("customer_id"), rs.getString("order_id"), rs.getLong("points"),
                rs.getLong("remaining"), Lot.Status.valueOf(rs.getString("status")), rs.getBigDecimal("order_total"),
                rs.getTimestamp("earned_at").toInstant(), instant(rs.getTimestamp("confirm_at")),
                instant(rs.getTimestamp("expires_at")));
    }

    Message toProto(LoyaltyEvent e) {
        return switch (e) {
            case LoyaltyEvent.Earned x -> PointsEarned.newBuilder().setCustomerId(x.customerId()).setOrderId(x.orderId())
                    .setPoints(x.points()).setLotId(x.lotId()).build();
            case LoyaltyEvent.Confirmed x -> PointsConfirmed.newBuilder().setCustomerId(x.customerId()).setLotId(x.lotId())
                    .setOrderId(x.orderId()).setPoints(x.points()).setValue(value(x.points())).build();
            case LoyaltyEvent.Redeemed x -> PointsRedeemed.newBuilder().setCustomerId(x.customerId())
                    .setRedemptionId(x.redemptionId()).setOrderId(x.orderId() == null ? "" : x.orderId())
                    .setPoints(x.points()).setValue(value(x.points())).build();
            case LoyaltyEvent.Reversed x -> PointsReversed.newBuilder().setCustomerId(x.customerId()).setOrderId(x.orderId())
                    .setPoints(x.points()).setConfirmed(x.confirmed()).setValue(value(x.points()))
                    .setReason(x.reason() == null ? "" : x.reason()).build();
            case LoyaltyEvent.Expired x -> PointsExpired.newBuilder().setCustomerId(x.customerId()).setLotId(x.lotId())
                    .setPoints(x.points()).setValue(value(x.points())).build();
            case LoyaltyEvent.TierChanged x -> TierChanged.newBuilder().setCustomerId(x.customerId())
                    .setFromTier(x.from().name()).setToTier(x.to().name()).build();
        };
    }

    private Money value(long points) {
        BigDecimal a = rules.pointValueUsd().multiply(BigDecimal.valueOf(points)).setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode("USD").setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }

    private static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
