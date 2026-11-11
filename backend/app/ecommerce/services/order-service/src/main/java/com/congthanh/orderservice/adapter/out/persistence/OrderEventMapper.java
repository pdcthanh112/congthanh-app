package com.congthanh.orderservice.adapter.out.persistence;

import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.ecommerce.order.v1.OrderConfirmed;
import com.congthanh.ecommerce.order.v1.OrderLine;
import com.congthanh.ecommerce.order.v1.OrderPlaced;
import com.congthanh.ecommerce.order.v1.ShippingAddress;
import com.congthanh.orderservice.domain.event.OrderEvent;
import com.google.protobuf.Message;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Event miền → protobuf trong api/proto/ecommerce/order/v1/order_events.proto (hợp đồng công khai). */
final class OrderEventMapper {

    private OrderEventMapper() {
    }

    static Message toProto(OrderEvent event) {
        return switch (event) {
            case OrderEvent.OrderPlaced e -> OrderPlaced.newBuilder()
                    .setOrderId(e.orderId())
                    .setCustomerId(e.customerId())
                    .addAllLines(e.lines().stream().map(l -> OrderLine.newBuilder()
                            .setProductId(l.productId())
                            .setQuantity(l.quantity())
                            .setUnitPrice(money(l.unitPrice()))
                            .build()).toList())
                    .setTotal(money(e.total()))
                    .setCheckoutWorkflowId(e.checkoutWorkflowId())
                    .setShippingAddress(address(e.shippingAddress()))
                    .build();
            case OrderEvent.OrderConfirmed e -> OrderConfirmed.newBuilder()
                    .setOrderId(e.orderId())
                    .setPaymentId(nullToEmpty(e.paymentId()))
                    .build();
            case OrderEvent.OrderCancelled e -> OrderCancelled.newBuilder()
                    .setOrderId(e.orderId())
                    .setReason(nullToEmpty(e.reason()))
                    .setReservationId(nullToEmpty(e.reservationId()))
                    .build();
        };
    }

    /** google.type.Money style: units + nanos cùng dấu. */
    static Money money(com.congthanh.orderservice.domain.model.Money m) {
        BigDecimal amount = m.amount().setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = amount.setScale(0, RoundingMode.DOWN);
        int nanos = amount.subtract(units).movePointRight(9).intValueExact();
        return Money.newBuilder().setCurrencyCode(m.currency()).setUnits(units.longValueExact()).setNanos(nanos).build();
    }

    /** shipping-service tạo vận đơn và fraud-service nhận diện địa chỉ từ đây; country là mã ISO 3166-1 alpha-2. */
    static ShippingAddress address(com.congthanh.orderservice.domain.model.Address a) {
        if (a == null) {
            return ShippingAddress.getDefaultInstance();
        }
        String country = nullToEmpty(a.country()).trim();
        return ShippingAddress.newBuilder()
                .setRecipientName(nullToEmpty(a.fullName()))
                .setPhone(nullToEmpty(a.phone()))
                .setLine1(nullToEmpty(a.line1()))
                .setLine2(nullToEmpty(a.line2()))
                .setWard(nullToEmpty(a.ward()))
                .setDistrict(nullToEmpty(a.district()))
                .setRegionCode(nullToEmpty(a.city()))
                .setPostalCode(nullToEmpty(a.postalCode()))
                .setCountryCode(country.length() == 2 ? country.toUpperCase(java.util.Locale.ROOT) : "")
                .build();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
