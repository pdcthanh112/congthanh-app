package com.congthanh.storedvalueservice.application.service;

import com.congthanh.storedvalueservice.application.port.in.StoredValueUseCases;
import com.congthanh.storedvalueservice.application.port.out.Ids;
import com.congthanh.storedvalueservice.application.port.out.StoredValueRepository;
import com.congthanh.storedvalueservice.domain.exception.StoredValueException;
import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Hold;
import com.congthanh.storedvalueservice.domain.model.Movement;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoredValueService implements StoredValueUseCases {

    /** Giữ chỗ dài hơn thời gian sống của saga checkout (cổng redirect có thể mất vài phút) rồi tự trả lại. */
    static final Duration HOLD_TTL = Duration.ofHours(2);
    static final Set<String> CREDIT_REASONS = Set.of("RETURN_REFUND", "GOODWILL", "ADJUSTMENT", "REFERRAL_REWARD");

    private final StoredValueRepository repo;
    private final Ids ids;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Override
    public List<Account> accounts(String customerId) {
        return repo.byCustomer(customerId);
    }

    @Override
    public List<Movement> history(String accountId, int limit) {
        return repo.history(accountId, Math.clamp(limit, 1, 500));
    }

    @Override
    public Movement credit(String customerId, BigDecimal amount, String currency, String reason, String reference) {
        if (reason == null || !CREDIT_REASONS.contains(reason)) {
            throw StoredValueException.invalid("credit reason must be one of " + CREDIT_REASONS);
        }
        return tx.execute(s -> {
            var done = repo.findMovement(Movement.Type.CREDIT, reference);
            if (done.isPresent()) {
                return done.get();
            }
            Account a = repo.lockAccount(customerId, Account.Kind.STORE_CREDIT, currency)
                    .orElseGet(() -> Account.open(ids.next(), customerId, Account.Kind.STORE_CREDIT, currency, clock.instant()));
            Movement m = a.credit(amount, reason, reference, clock.instant());
            repo.save(a);
            return m;
        });
    }

    @Override
    public Movement hold(String customerId, String holdId, String orderId, BigDecimal amount, String currency) {
        return tx.execute(s -> {
            var done = repo.findMovement(Movement.Type.HOLD, holdId);
            if (done.isPresent()) {
                return done.get();
            }
            Account a = repo.lockAccount(customerId, Account.Kind.STORE_CREDIT, currency)
                    .orElseThrow(() -> StoredValueException.insufficient("customer has no store credit in " + currency));
            Movement m = a.hold(holdId, orderId, amount, clock.instant());
            repo.save(a);
            repo.saveHold(new Hold(holdId, a.id(), orderId, m.amount(), Hold.Status.HELD, clock.instant().plus(HOLD_TTL)));
            return m;
        });
    }

    @Override
    public void capture(String holdId) {
        tx.executeWithoutResult(s -> {
            Hold h = repo.lockHold(holdId).orElseThrow(() -> StoredValueException.notFound("hold " + holdId));
            if (h.status() == Hold.Status.CAPTURED) {
                return;
            }
            Hold captured = h.captured();
            Account a = repo.lockAccountById(h.accountId()).orElseThrow();
            a.capture(h, clock.instant());
            repo.save(a);
            repo.saveHold(captured);
        });
    }

    @Override
    public void release(String holdId, String reason) {
        tx.executeWithoutResult(s -> {
            var found = repo.lockHold(holdId);
            if (found.isEmpty() || found.get().status() == Hold.Status.RELEASED) {
                return; // bù trừ trước khi hold kịp tạo, hoặc đã trả
            }
            Hold h = found.get();
            Hold released = h.released();
            Account a = repo.lockAccountById(h.accountId()).orElseThrow();
            a.release(h, reason, clock.instant());
            repo.save(a);
            repo.saveHold(released);
        });
    }

    @Override
    public void setFrozen(String accountId, boolean frozen) {
        tx.executeWithoutResult(s -> {
            Account a = repo.lockAccountById(accountId)
                    .orElseThrow(() -> StoredValueException.notFound("account " + accountId));
            if (frozen) {
                a.freeze();
            } else {
                a.unfreeze();
            }
            repo.save(a);
        });
    }

    @Override
    public int releaseExpired() {
        List<Hold> expired = repo.expiredHolds(clock.instant(), 200);
        expired.forEach(h -> release(h.id(), "EXPIRED"));
        return expired.size();
    }
}
