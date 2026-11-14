package com.congthanh.subscriptionservice.application.service;

import com.congthanh.subscriptionservice.application.port.in.SubscriptionUseCases;
import com.congthanh.subscriptionservice.application.port.out.Ids;
import com.congthanh.subscriptionservice.application.port.out.RenewalOrders;
import com.congthanh.subscriptionservice.application.port.out.SubscriptionRepository;
import com.congthanh.subscriptionservice.domain.exception.SubscriptionException;
import com.congthanh.subscriptionservice.domain.model.Line;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class SubscriptionService implements SubscriptionUseCases {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);
    /** Ngày giao tính theo giờ New York (ADR 0015). */
    static final ZoneId ZONE = ZoneId.of("America/New_York");

    private final SubscriptionRepository repo;
    private final RenewalOrders orders;
    private final Ids ids;
    private final Subscription.Schedule schedule;
    private final TransactionTemplate tx;
    private final Clock clock;

    private LocalDate today() {
        return LocalDate.now(clock.withZone(ZONE));
    }

    @Override
    public Subscription create(String customerId, List<Line> lines, int intervalDays, LocalDate firstDelivery,
                               String paymentMethodRef, String addressId) {
        return tx.execute(s -> {
            Subscription sub = Subscription.create(ids.next(), customerId, lines, intervalDays, firstDelivery,
                    paymentMethodRef, addressId, today(), clock.instant());
            repo.save(sub);
            return sub;
        });
    }

    @Override
    public List<Subscription> mine(String customerId) {
        return repo.byCustomer(customerId);
    }

    @Override
    public Subscription get(String id, String caller, boolean staff) {
        Subscription s = repo.findById(id).orElseThrow(() -> SubscriptionException.notFound("subscription " + id));
        if (!staff) {
            s.requireOwner(caller);
        }
        return s;
    }

    @Override
    public Subscription change(String id, String caller, List<Line> lines, Integer intervalDays) {
        return mutate(id, caller, s -> s.change(lines, intervalDays));
    }

    @Override
    public Subscription skip(String id, String caller) {
        return mutate(id, caller, Subscription::skipNext);
    }

    @Override
    public Subscription pause(String id, String caller, LocalDate until) {
        return mutate(id, caller, s -> s.pause(until, today()));
    }

    @Override
    public Subscription resume(String id, String caller) {
        return mutate(id, caller, s -> s.resume(today()));
    }

    @Override
    public Subscription cancel(String id, String caller, String reason) {
        return mutate(id, caller, s -> s.cancel(reason == null || reason.isBlank() ? "CUSTOMER_CANCELLED" : reason));
    }

    @Override
    public List<Subscription> byStatus(SubscriptionStatus status, int limit) {
        return repo.byStatus(status, Math.clamp(limit, 1, 500));
    }

    @Override
    public int tick() {
        LocalDate today = today();
        Instant now = clock.instant();
        LocalDate horizon = today.plusDays(Math.max(schedule.reminderLead().toDays(), schedule.orderLead().toDays()));
        // Nhận việc trong một transaction ngắn (khoá SKIP LOCKED), xử lý từng gói trong transaction riêng.
        List<String> due = tx.execute(s -> repo.claimDue(horizon, now, 100).stream().map(Subscription::id).toList());
        int n = 0;
        for (String id : due) {
            try {
                if (handle(id, today, now)) {
                    n++;
                }
            } catch (RuntimeException e) {
                log.warn("subscription {} tick failed: {}", id, e.toString());
            }
        }
        return n;
    }

    private boolean handle(String id, LocalDate today, Instant now) {
        Subscription s = repo.findById(id).orElseThrow();
        boolean renew = s.renewalDue(today, now, schedule);
        if (s.reminderDue(today, schedule)) {
            s.reminded();
        }
        if (!renew || !orders.enabled()) {
            if (renew) {
                log.info("subscription {} cycle {} is due but renewal orders are disabled", s.id(), s.cycle());
            }
            tx.executeWithoutResult(t -> repo.save(s));
            return renew || s.remindedCycle() == s.cycle();
        }
        String key = s.renewalKey();
        try {
            String orderId = orders.place(s, key);
            int cycle = s.cycle();
            s.renewed(orderId);
            tx.executeWithoutResult(t -> {
                repo.recordRenewalOrder(orderId, s.id(), cycle);
                repo.save(s);
            });
            return true;
        } catch (RenewalOrders.Rejected e) {
            s.renewalFailed(e.getMessage(), now, schedule);
        }
        tx.executeWithoutResult(t -> repo.save(s));
        return true;
    }

    @Override
    public void renewalOrderCancelled(String orderId, String reason) {
        repo.renewalOrder(orderId).ifPresent(r -> tx.executeWithoutResult(t -> {
            Subscription s = repo.findById(r.subscriptionId()).orElseThrow();
            s.renewalOrderCancelled(r.cycle(), reason == null || reason.isBlank() ? "ORDER_CANCELLED" : reason,
                    clock.instant(), schedule);
            repo.save(s);
        }));
    }

    private Subscription mutate(String id, String caller, Consumer<Subscription> change) {
        return tx.execute(t -> {
            Subscription s = repo.findById(id).orElseThrow(() -> SubscriptionException.notFound("subscription " + id));
            s.requireOwner(caller);
            change.accept(s);
            repo.save(s);
            return s;
        });
    }
}
