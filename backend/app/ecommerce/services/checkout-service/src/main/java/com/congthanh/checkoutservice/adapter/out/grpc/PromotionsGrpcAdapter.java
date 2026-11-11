package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Promotions;
import com.congthanh.checkoutservice.domain.model.Money;
import com.congthanh.promotionservice.grpc.PromotionServiceGrpc;
import com.congthanh.promotionservice.grpc.QuoteDiscountRequest;
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
    public Quote quote(String code, String customerId, Money subtotal) {
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS).quoteDiscount(QuoteDiscountRequest.newBuilder()
                    .setCode(code).setCustomerId(customerId).setSubtotal(Protos.toProto(subtotal)).build());
            return new Quote(res.getValid(), Protos.fromProto(res.getDiscount(), subtotal.currency()), res.getReason());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == io.grpc.Status.Code.NOT_FOUND) {
                return new Quote(false, Money.zero(subtotal.currency()), "NOT_FOUND");
            }
            throw Protos.translate("promotion-service", e);
        }
    }
}
