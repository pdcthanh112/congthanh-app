package com.congthanh.orderservice.adapter.out.grpc;

import com.congthanh.orderservice.application.port.out.Promotions;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.promotionservice.grpc.CancelRedemptionRequest;
import com.congthanh.promotionservice.grpc.PromotionServiceGrpc;
import com.congthanh.promotionservice.grpc.QuoteDiscountRequest;
import com.congthanh.promotionservice.grpc.RedeemPromotionRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
class PromotionsGrpcAdapter implements Promotions {

    private final PromotionServiceGrpc.PromotionServiceBlockingStub stub;

    PromotionsGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = PromotionServiceGrpc.newBlockingStub(channels.createChannel("promotion"));
    }

    @Override
    @Retry(name = "promotion")
    @CircuitBreaker(name = "promotion")
    public DiscountQuote quote(String code, String customerId, Money subtotal) {
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS).quoteDiscount(QuoteDiscountRequest.newBuilder()
                    .setCode(code).setCustomerId(customerId).setSubtotal(Protos.toProto(subtotal)).build());
            return new DiscountQuote(res.getValid(), Protos.fromProto(res.getDiscount(), subtotal.currency()), res.getReason());
        } catch (StatusRuntimeException e) {
            throw Protos.translate("promotion-service", e);
        }
    }

    /** Gọi từ activity Temporal: retry do Temporal lo, không bọc Resilience4j để tránh retry chồng retry. */
    @Override
    public DiscountQuote redeem(String code, String customerId, String orderId, Money subtotal) {
        try {
            var res = stub.withDeadlineAfter(5, TimeUnit.SECONDS).redeemPromotion(RedeemPromotionRequest.newBuilder()
                    .setCode(code).setCustomerId(customerId).setOrderId(orderId)
                    .setSubtotal(Protos.toProto(subtotal)).build());
            return new DiscountQuote(res.getRedeemed(), Protos.fromProto(res.getDiscount(), subtotal.currency()), res.getReason());
        } catch (StatusRuntimeException e) {
            throw Protos.translate("promotion-service", e);
        }
    }

    @Override
    public void cancelRedemption(String orderId, String reason) {
        try {
            stub.withDeadlineAfter(5, TimeUnit.SECONDS).cancelRedemption(CancelRedemptionRequest.newBuilder()
                    .setOrderId(orderId).setReason(reason == null ? "" : reason).build());
        } catch (StatusRuntimeException e) {
            throw Protos.translate("promotion-service", e);
        }
    }
}
