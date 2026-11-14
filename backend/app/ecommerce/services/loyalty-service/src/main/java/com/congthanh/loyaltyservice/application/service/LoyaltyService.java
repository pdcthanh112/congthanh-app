package com.congthanh.loyaltyservice.application.service;

import com.congthanh.loyaltyservice.application.port.in.LoyaltyUseCases;
import com.congthanh.loyaltyservice.application.port.out.Ids;
import com.congthanh.loyaltyservice.application.port.out.LoyaltyRepository;
import com.congthanh.loyaltyservice.application.port.out.LoyaltyRepository.Redemption;
import com.congthanh.loyaltyservice.domain.exception.LoyaltyException;
import com.congthanh.loyaltyservice.domain.model.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LoyaltyService implements LoyaltyUseCases {

    private final LoyaltyRepository repo;
    private final Ids ids;
    private final Member.Rules rules;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Override
    public Summary summary(String customerId) {
        Member m = repo.find(customerId).orElseGet(() -> new Member(customerId, null, List.of(), 0));
        return new Summary(customerId, m.tier(), m.available(), m.pending(), m.value(m.available(), rules), m.lots());
    }

    @Override
    public void orderConfirmed(String orderId, String customerId, BigDecimal totalUsd) {
        if (customerId == null || customerId.isBlank() || totalUsd == null || totalUsd.signum() <= 0) {
            return;
        }
        tx.executeWithoutResult(s -> {
            Member m = repo.lock(customerId);
            m.earn(ids.next(), orderId, totalUsd, clock.instant());
            repo.save(m);
        });
    }

    @Override
    public void orderDelivered(String orderId, Instant at) {
        repo.customerOfOrder(orderId).ifPresent(c -> tx.executeWithoutResult(s -> {
            Member m = repo.lock(c);
            m.delivered(orderId, at, rules);
            m.confirmDue(clock.instant(), rules);
            repo.save(m);
        }));
    }

    @Override
    public void refunded(String orderId, BigDecimal amountUsd, String reason) {
        repo.customerOfOrder(orderId).ifPresent(c -> tx.executeWithoutResult(s -> {
            Member m = repo.lock(c);
            m.reverse(orderId, amountUsd, reason);
            repo.save(m);
        }));
    }

    @Override
    public void orderCancelled(String orderId) {
        refunded(orderId, new BigDecimal("1000000000"), "ORDER_CANCELLED");
    }

    @Override
    public BigDecimal reserve(String redemptionId, String customerId, String orderId, long points) {
        return tx.execute(s -> {
            var existing = repo.lockRedemption(redemptionId);
            Member m = repo.lock(customerId);
            if (existing.isPresent()) {
                return m.value(existing.get().points(), rules);
            }
            List<Member.Allocation> allocations = m.reserve(points, rules);
            repo.save(m);
            repo.saveRedemption(new Redemption(redemptionId, customerId, orderId, points, "RESERVED", allocations));
            return m.value(points, rules);
        });
    }

    @Override
    public void commit(String redemptionId) {
        tx.executeWithoutResult(s -> {
            Redemption r = repo.lockRedemption(redemptionId)
                    .orElseThrow(() -> LoyaltyException.notFound("redemption " + redemptionId));
            if (r.status().equals("REDEEMED")) {
                return;
            }
            if (r.status().equals("RELEASED")) {
                throw LoyaltyException.conflict("redemption " + redemptionId + " was released");
            }
            Member m = repo.lock(r.customerId());
            m.redeemed(r.id(), r.orderId(), r.points());
            repo.save(m);
            repo.saveRedemption(new Redemption(r.id(), r.customerId(), r.orderId(), r.points(), "REDEEMED", r.allocations()));
        });
    }

    @Override
    public void release(String redemptionId) {
        tx.executeWithoutResult(s -> {
            var found = repo.lockRedemption(redemptionId);
            if (found.isEmpty() || !found.get().status().equals("RESERVED")) {
                return;
            }
            Redemption r = found.get();
            Member m = repo.lock(r.customerId());
            m.release(r.allocations(), clock.instant());
            repo.save(m);
            repo.saveRedemption(new Redemption(r.id(), r.customerId(), r.orderId(), r.points(), "RELEASED", r.allocations()));
        });
    }

    @Override
    public int housekeeping() {
        Instant now = clock.instant();
        List<String> due = repo.dueForHousekeeping(now, 500);
        for (String c : due) {
            tx.executeWithoutResult(s -> {
                Member m = repo.lock(c);
                m.confirmDue(now, rules);
                m.expireDue(now);
                m.retier(now);
                repo.save(m);
                repo.markHoused(c, now);
            });
        }
        return due.size();
    }
}
