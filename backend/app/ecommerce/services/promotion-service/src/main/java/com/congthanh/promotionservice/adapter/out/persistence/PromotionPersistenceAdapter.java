package com.congthanh.promotionservice.adapter.out.persistence;

import com.congthanh.platform.outbox.OutboxWriter;
import com.congthanh.promotionservice.application.port.out.PromotionRepository;
import com.congthanh.promotionservice.domain.event.PromotionEvent;
import com.congthanh.promotionservice.domain.model.DiscountRule;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.PromotionType;
import com.congthanh.promotionservice.domain.model.Redemption;
import com.congthanh.promotionservice.grpc.PromotionActivated;
import com.congthanh.promotionservice.grpc.PromotionExpired;
import com.congthanh.promotionservice.grpc.PromotionRedeemed;
import com.congthanh.promotionservice.grpc.RedemptionCancelled;
import com.google.protobuf.Message;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
class PromotionPersistenceAdapter implements PromotionRepository {

    static final String AGGREGATE = "promotion";

    private final PromotionJpaRepository promotions;
    private final RedemptionJpaRepository redemptions;
    private final OutboxWriter outbox;

    PromotionPersistenceAdapter(PromotionJpaRepository promotions, RedemptionJpaRepository redemptions, OutboxWriter outbox) {
        this.promotions = promotions;
        this.redemptions = redemptions;
        this.outbox = outbox;
    }

    @Override
    public Optional<Promotion> findById(long id) {
        return promotions.findById(id).map(PromotionPersistenceAdapter::toDomain);
    }

    @Override
    public Optional<Promotion> findByCode(String code) {
        return promotions.findByCode(code).map(PromotionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<Promotion> lockByCode(String code) {
        return promotions.lockByCode(code).map(PromotionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public Optional<Promotion> lockById(long id) {
        return promotions.lockById(id).map(PromotionPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional
    public Promotion save(Promotion p) {
        PromotionEntity e = p.id() == null ? new PromotionEntity()
                : promotions.findById(p.id()).orElseThrow();
        if (p.id() != null && e.version != p.version()) {
            throw new ObjectOptimisticLockingFailureException(PromotionEntity.class, p.id());
        }
        boolean created = p.id() == null;
        e.code = p.code();
        e.type = p.type().name();
        e.discountKind = p.rule().kind().name();
        e.discountValue = p.rule().value();
        e.currency = p.rule().currency();
        e.minSubtotal = p.rule().minSubtotal();
        e.maxDiscount = p.rule().maxDiscount();
        e.usageLimit = p.usageLimit();
        e.perCustomerLimit = p.perCustomerLimit();
        e.usedCount = p.usedCount();
        e.startsAt = p.startsAt();
        e.endsAt = p.endsAt();
        e.active = p.active();
        e.description = p.description();
        PromotionEntity saved = promotions.saveAndFlush(e);
        if (created) {
            p.announceActivated(saved.id);
        }
        for (PromotionEvent ev : p.pullEvents()) {
            outbox.write(AGGREGATE, String.valueOf(saved.id), toProto(ev));
        }
        return toDomain(saved);
    }

    @Override
    public Optional<Redemption> findRedemption(String orderId) {
        return redemptions.findById(orderId).map(r -> new Redemption(r.promotionId, r.orderId, r.customerId,
                r.discount, r.currency, r.cancelled, r.redeemedAt));
    }

    @Override
    public int countActiveRedemptions(long promotionId, String customerId) {
        return redemptions.countByPromotionIdAndCustomerIdAndCancelledFalse(promotionId, customerId);
    }

    @Override
    @Transactional
    public void saveRedemption(Redemption r) {
        RedemptionEntity e = redemptions.findById(r.orderId()).orElseGet(RedemptionEntity::new);
        e.orderId = r.orderId();
        e.promotionId = r.promotionId();
        e.customerId = r.customerId();
        e.discount = r.discount();
        e.currency = r.currency();
        e.cancelled = r.cancelled();
        e.redeemedAt = r.redeemedAt();
        redemptions.save(e);
    }

    static Promotion toDomain(PromotionEntity e) {
        return Promotion.rehydrate(e.id, e.code, PromotionType.valueOf(e.type),
                new DiscountRule(DiscountRule.Kind.valueOf(e.discountKind), e.discountValue, e.currency, e.minSubtotal, e.maxDiscount),
                e.usageLimit, e.perCustomerLimit, e.usedCount, e.startsAt, e.endsAt, e.active, e.description, e.version);
    }

    static Message toProto(PromotionEvent ev) {
        return switch (ev) {
            case PromotionEvent.PromotionActivated a -> PromotionActivated.newBuilder()
                    .setPromotionId(String.valueOf(a.promotionId())).setCode(a.code())
                    .setStartsAt(a.startsAt() == null ? 0 : a.startsAt().getEpochSecond())
                    .setEndsAt(a.endsAt() == null ? 0 : a.endsAt().getEpochSecond()).build();
            case PromotionEvent.PromotionExpired x -> PromotionExpired.newBuilder()
                    .setPromotionId(String.valueOf(x.promotionId())).build();
            case PromotionEvent.PromotionRedeemed r -> PromotionRedeemed.newBuilder()
                    .setPromotionId(String.valueOf(r.promotionId())).setCode(r.code())
                    .setOrderId(r.orderId()).setCustomerId(r.customerId()).build();
            case PromotionEvent.RedemptionCancelled c -> RedemptionCancelled.newBuilder()
                    .setPromotionId(String.valueOf(c.promotionId())).setOrderId(c.orderId())
                    .setReason(c.reason() == null ? "" : c.reason()).build();
        };
    }
}
