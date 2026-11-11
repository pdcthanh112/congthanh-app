package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Tax;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.Money;
import com.congthanh.taxservice.grpc.CalculateTaxRequest;
import com.congthanh.taxservice.grpc.LineTax;
import com.congthanh.taxservice.grpc.TaxAddress;
import com.congthanh.taxservice.grpc.TaxLine;
import com.congthanh.taxservice.grpc.TaxServiceGrpc;
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
class TaxGrpcAdapter implements Tax {

    private final TaxServiceGrpc.TaxServiceBlockingStub stub;

    TaxGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = TaxServiceGrpc.newBlockingStub(channels.createChannel("tax"));
    }

    @Override
    @Retry(name = "tax")
    @CircuitBreaker(name = "tax")
    public Map<String, Money> calculate(Address address, List<TaxableLine> lines) {
        if (lines.isEmpty()) {
            return Map.of();
        }
        String currency = lines.getFirst().taxable().currency();
        var req = CalculateTaxRequest.newBuilder().setAddress(TaxAddress.newBuilder().setCountryCode(address.country())
                .setRegionCode(address.city()).setPostalCode(address.postalCode() == null ? "" : address.postalCode()));
        lines.forEach(l -> req.addLines(TaxLine.newBuilder().setLineId(l.lineId()).setTaxClassId(l.taxClassId())
                .setTaxableAmount(Protos.toProto(l.taxable()))));
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS).calculateTax(req.build());
            Map<String, Money> out = new HashMap<>();
            for (LineTax t : res.getLinesList()) {
                out.put(t.getLineId(), Protos.fromProto(t.getTax(), currency));
            }
            return out;
        } catch (StatusRuntimeException e) {
            throw Protos.translate("tax-service", e);
        }
    }
}
