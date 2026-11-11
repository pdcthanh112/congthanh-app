package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.application.port.out.Orders;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.ecommerce.order.v1.OrderAddress;
import com.congthanh.ecommerce.order.v1.OrderServiceGrpc;
import com.congthanh.ecommerce.order.v1.PlaceOrderRequest;
import com.congthanh.ecommerce.order.v1.PricedLine;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.StatusRuntimeException;
import org.springframework.grpc.client.GrpcChannelFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
class OrdersGrpcAdapter implements Orders {

    private final OrderServiceGrpc.OrderServiceBlockingStub stub;

    OrdersGrpcAdapter(GrpcChannelFactory channels) {
        this.stub = OrderServiceGrpc.newBlockingStub(channels.createChannel("order"));
    }

    @Override
    @Retry(name = "order")
    public Placed place(CheckoutSession s) {
        var t = s.totals();
        var p = s.payment();
        var req = PlaceOrderRequest.newBuilder()
                .setOrderId(s.orderId())
                .setCustomerId(s.customerId())
                .setPromotionCode(s.promotionCode() == null ? "" : s.promotionCode())
                .setShippingFee(Protos.toProto(t.shippingFee()))
                .setTax(Protos.toProto(t.tax()))
                .setPaymentMethod(p.method())
                .setPaymentToken(nz(p.token()))
                .setReturnUrl(nz(p.returnUrl()))
                .setClientIp(nz(p.clientIp()))
                .setShippingAddress(address(s.shippingAddress()))
                .setNote(nz(s.note()));
        if (s.billingAddress() != null) {
            req.setBillingAddress(address(s.billingAddress()));
        }
        s.lines().forEach(l -> req.addLines(PricedLine.newBuilder().setProductId(l.productId()).setQuantity(l.quantity())
                .setUnitPrice(Protos.toProto(l.unitPrice()))));
        try {
            var res = stub.withDeadlineAfter(5, TimeUnit.SECONDS).placeOrder(req.build());
            return new Placed(res.getOrderId(), res.getOrderCode(), res.getStatus());
        } catch (StatusRuntimeException e) {
            throw Protos.translate("order-service", e);
        }
    }

    static OrderAddress address(Address a) {
        return OrderAddress.newBuilder().setFullName(a.fullName()).setPhone(a.phone()).setLine1(a.line1())
                .setLine2(nz(a.line2())).setWard(nz(a.ward())).setDistrict(nz(a.district())).setCity(a.city())
                .setCountryCode(a.country()).setPostalCode(nz(a.postalCode())).build();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
