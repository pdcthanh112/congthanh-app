package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Shipping;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession.ShippingOption;
import com.congthanh.checkoutservice.domain.model.Money;
import com.congthanh.ecommerce.order.v1.ShippingAddress;
import com.congthanh.ecommerce.shipping.v1.Parcel;
import com.congthanh.ecommerce.shipping.v1.QuoteShippingRequest;
import com.congthanh.ecommerce.shipping.v1.ShippingServiceGrpc;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Component
class ShippingGrpcAdapter implements Shipping {

    private final ShippingServiceGrpc.ShippingServiceBlockingStub stub;

    ShippingGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = ShippingServiceGrpc.newBlockingStub(channels.createChannel("shipping"));
    }

    static ShippingAddress toProto(Address a) {
        return ShippingAddress.newBuilder().setRecipientName(a.fullName()).setPhone(a.phone()).setLine1(a.line1())
                .setLine2(nz(a.line2())).setWard(nz(a.ward())).setDistrict(nz(a.district())).setRegionCode(a.city())
                .setPostalCode(nz(a.postalCode())).setCountryCode(a.country()).build();
    }

    @Override
    @Retry(name = "shipping")
    @CircuitBreaker(name = "shipping")
    public List<ShippingOption> quote(Address destination, int weightGrams, Money declaredValue) {
        try {
            var res = stub.withDeadlineAfter(2, TimeUnit.SECONDS).quoteShipping(QuoteShippingRequest.newBuilder()
                    .setDestination(toProto(destination))
                    .setParcel(Parcel.newBuilder().setWeightGrams(weightGrams).setDeclaredValue(Protos.toProto(declaredValue)))
                    .build());
            return res.getQuotesList().stream().map(q -> new ShippingOption(q.getCarrier(), q.getServiceCode(),
                    q.getServiceName(), Protos.fromProto(q.getFee(), declaredValue.currency()),
                    q.getEstimatedDaysMin(), q.getEstimatedDaysMax())).toList();
        } catch (StatusRuntimeException e) {
            throw Protos.translate("shipping-service", e);
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
