package com.congthanh.promotionservice.adapter.in.grpc;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.promotionservice.application.port.in.CheckoutDiscounts;
import com.congthanh.promotionservice.application.port.in.PromotionAdmin;
import com.congthanh.promotionservice.domain.exception.PromotionException;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.Quote;
import com.congthanh.promotionservice.grpc.CancelRedemptionRequest;
import com.congthanh.promotionservice.grpc.CancelRedemptionResponse;
import com.congthanh.promotionservice.grpc.GetPromotionByCodeRequest;
import com.congthanh.promotionservice.grpc.PromotionRequest;
import com.congthanh.promotionservice.grpc.PromotionResponse;
import com.congthanh.promotionservice.grpc.PromotionServiceGrpc;
import com.congthanh.promotionservice.grpc.PromotionType;
import com.congthanh.promotionservice.grpc.QuoteDiscountRequest;
import com.congthanh.promotionservice.grpc.QuoteDiscountResponse;
import com.congthanh.promotionservice.grpc.RedeemPromotionRequest;
import com.congthanh.promotionservice.grpc.RedeemPromotionResponse;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.function.Supplier;

@GrpcService
class PromotionGrpcService extends PromotionServiceGrpc.PromotionServiceImplBase {

    private final CheckoutDiscounts discounts;
    private final PromotionAdmin admin;

    PromotionGrpcService(CheckoutDiscounts discounts, PromotionAdmin admin) {
        this.discounts = discounts;
        this.admin = admin;
    }

    @Override
    public void getPromotion(PromotionRequest req, StreamObserver<PromotionResponse> out) {
        respond(out, () -> toProto(admin.byId(req.getPromotionId())));
    }

    @Override
    public void getPromotionByCode(GetPromotionByCodeRequest req, StreamObserver<PromotionResponse> out) {
        respond(out, () -> toProto(admin.byCode(req.getCode())));
    }

    @Override
    public void quoteDiscount(QuoteDiscountRequest req, StreamObserver<QuoteDiscountResponse> out) {
        respond(out, () -> {
            Quote q = discounts.quote(req.getCode(), req.getCustomerId(), amount(req.getSubtotal()), req.getSubtotal().getCurrencyCode());
            return QuoteDiscountResponse.newBuilder().setValid(q.valid()).setDiscount(money(q.discount(), q.currency()))
                    .setReason(nz(q.reason())).setPromotionId(q.promotionId() == null ? "" : q.promotionId().toString()).build();
        });
    }

    @Override
    public void redeemPromotion(RedeemPromotionRequest req, StreamObserver<RedeemPromotionResponse> out) {
        respond(out, () -> {
            Quote q = discounts.redeem(req.getCode(), req.getCustomerId(), req.getOrderId(),
                    amount(req.getSubtotal()), req.getSubtotal().getCurrencyCode());
            return RedeemPromotionResponse.newBuilder().setRedeemed(q.valid())
                    .setDiscount(money(q.discount(), q.currency())).setReason(nz(q.reason())).build();
        });
    }

    @Override
    public void cancelRedemption(CancelRedemptionRequest req, StreamObserver<CancelRedemptionResponse> out) {
        respond(out, () -> CancelRedemptionResponse.newBuilder()
                .setCancelled(discounts.cancelRedemption(req.getOrderId(), req.getReason())).build());
    }

    private static <T> void respond(StreamObserver<T> out, Supplier<T> call) {
        try {
            out.onNext(call.get());
            out.onCompleted();
        } catch (PromotionException e) {
            Status s = switch (e.kind()) {
                case INVALID -> Status.INVALID_ARGUMENT;
                case NOT_FOUND -> Status.NOT_FOUND;
                case CONFLICT -> Status.FAILED_PRECONDITION;
            };
            out.onError(s.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    private static PromotionResponse toProto(Promotion p) {
        var b = PromotionResponse.newBuilder()
                .setId(p.id()).setCode(p.code())
                .setType(PromotionType.valueOf(p.type().name()))
                .setValue(p.rule().value().floatValue())
                .setUsageLimit(p.usageLimit())
                .setDescription(nz(p.description()))
                .setStatus(p.active() ? "ACTIVE" : "INACTIVE");
        if (p.startsAt() != null) {
            b.setStartDate(ts(p.startsAt()));
        }
        if (p.endsAt() != null) {
            b.setEndDate(ts(p.endsAt()));
        }
        return b.build();
    }

    static BigDecimal amount(Money m) {
        return BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9));
    }

    static Money money(BigDecimal amount, String currency) {
        BigDecimal a = amount.setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = a.setScale(0, RoundingMode.DOWN);
        return Money.newBuilder().setCurrencyCode(nz(currency)).setUnits(units.longValueExact())
                .setNanos(a.subtract(units).movePointRight(9).intValueExact()).build();
    }

    private static Timestamp ts(Instant i) {
        return Timestamp.newBuilder().setSeconds(i.getEpochSecond()).setNanos(i.getNano()).build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
