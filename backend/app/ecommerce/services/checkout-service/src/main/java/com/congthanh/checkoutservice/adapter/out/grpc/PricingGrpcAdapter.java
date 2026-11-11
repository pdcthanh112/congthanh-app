package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Pricing;
import com.congthanh.ecommerce.pricing.v1.PricingContext;
import com.congthanh.ecommerce.pricing.v1.PricingServiceGrpc;
import com.congthanh.ecommerce.pricing.v1.QuoteLine;
import com.congthanh.ecommerce.pricing.v1.QuoteLinesRequest;
import com.congthanh.ecommerce.pricing.v1.QuotedLine;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Component
class PricingGrpcAdapter implements Pricing {

    private final PricingServiceGrpc.PricingServiceBlockingStub stub;

    PricingGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = PricingServiceGrpc.newBlockingStub(channels.createChannel("pricing"));
    }

    @Override
    @Retry(name = "pricing")
    @CircuitBreaker(name = "pricing")
    public Result quote(Map<String, Integer> quantities, String currency, List<String> customerGroups) {
        var req = QuoteLinesRequest.newBuilder()
                .setContext(PricingContext.newBuilder().setCurrencyCode(currency).addAllCustomerGroups(customerGroups));
        quantities.forEach((p, q) -> req.addLines(QuoteLine.newBuilder().setLineId(p).setProductId(p).setQuantity(q)));
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS).quoteLines(req.build());
            Map<String, Line> lines = new HashMap<>();
            for (QuotedLine l : res.getLinesList()) {
                var unit = Protos.fromProto(l.getUnitPrice(), currency);
                lines.put(l.getProductId(), new Line(l.getProductId(), l.getQuantity(), unit,
                        l.hasCompareAt() ? Protos.fromProto(l.getCompareAt(), currency) : unit,
                        Protos.fromProto(l.getLineTotal(), currency)));
            }
            return new Result(lines, List.copyOf(res.getUnpricedProductIdsList()));
        } catch (StatusRuntimeException e) {
            throw Protos.translate("pricing-service", e);
        }
    }
}
