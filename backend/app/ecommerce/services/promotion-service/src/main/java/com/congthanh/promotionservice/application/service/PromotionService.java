package com.congthanh.promotionservice.application.service;

import com.congthanh.promotionservice.application.port.in.CheckoutDiscounts;
import com.congthanh.promotionservice.application.port.in.PromotionAdmin;
import com.congthanh.promotionservice.application.port.out.PromotionRepository;
import com.congthanh.promotionservice.domain.exception.PromotionException;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.Quote;
import com.congthanh.promotionservice.domain.model.Redemption;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Locale;
import java.util.Optional;

@Service
public class PromotionService implements CheckoutDiscounts, PromotionAdmin {

    private final PromotionRepository repo;
    private final Clock clock;

    public PromotionService(PromotionRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Quote quote(String code, String customerId, BigDecimal subtotal, String currency) {
        Optional<Promotion> p = repo.findByCode(normalize(code));
        if (p.isEmpty()) {
            return Quote.rejected("NOT_FOUND", currency);
        }
        int uses = customerId == null ? 0 : repo.countActiveRedemptions(p.get().id(), customerId);
        return p.get().quote(subtotal, currency, uses, clock.instant());
    }

    @Override
    @Transactional
    public Quote redeem(String code, String customerId, String orderId, BigDecimal subtotal, String currency) {
        Optional<Redemption> existing = repo.findRedemption(orderId);
        if (existing.isPresent()) {
            Redemption r = existing.get();
            return r.cancelled() ? Quote.rejected("CANCELLED", currency)
                    : new Quote(true, r.discount(), r.currency(), null, r.promotionId());
        }
        Optional<Promotion> locked = repo.lockByCode(normalize(code));
        if (locked.isEmpty()) {
            return Quote.rejected("NOT_FOUND", currency);
        }
        Promotion p = locked.get();
        int uses = repo.countActiveRedemptions(p.id(), customerId);
        Quote q = p.redeem(subtotal, currency, uses, orderId, customerId, clock.instant());
        if (q.valid()) {
            repo.saveRedemption(new Redemption(p.id(), orderId, customerId, q.discount(), currency, false, clock.instant()));
            repo.save(p);
        }
        return q;
    }

    @Override
    @Transactional
    public boolean cancelRedemption(String orderId, String reason) {
        Optional<Redemption> r = repo.findRedemption(orderId);
        if (r.isEmpty() || r.get().cancelled()) {
            return false;
        }
        Promotion p = repo.lockById(r.get().promotionId())
                .orElseThrow(() -> PromotionException.notFound("promotion " + r.get().promotionId()));
        p.releaseUse(orderId, reason);
        repo.saveRedemption(r.get().cancel());
        repo.save(p);
        return true;
    }

    @Override
    @Transactional
    public Promotion create(PromotionSpec s) {
        if (repo.findByCode(normalize(s.code())).isPresent()) {
            throw PromotionException.conflict("promotion code " + s.code() + " already exists");
        }
        return repo.save(Promotion.create(s.code(), s.type(), s.rule(), s.usageLimit(), s.perCustomerLimit(),
                s.startsAt(), s.endsAt(), s.description()));
    }

    @Override
    @Transactional
    public Promotion update(long id, PromotionSpec s) {
        Promotion p = repo.lockById(id).orElseThrow(() -> PromotionException.notFound("promotion " + id));
        p.update(s.type(), s.rule(), s.usageLimit(), s.perCustomerLimit(), s.startsAt(), s.endsAt(), s.description());
        return repo.save(p);
    }

    @Override
    @Transactional
    public void deactivate(long id) {
        Promotion p = repo.lockById(id).orElseThrow(() -> PromotionException.notFound("promotion " + id));
        p.deactivate();
        repo.save(p);
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion byId(long id) {
        return repo.findById(id).orElseThrow(() -> PromotionException.notFound("promotion " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Promotion byCode(String code) {
        return repo.findByCode(normalize(code)).orElseThrow(() -> PromotionException.notFound("promotion " + code));
    }

    private static String normalize(String code) {
        return code == null ? "" : code.trim().toUpperCase(Locale.ROOT);
    }
}
