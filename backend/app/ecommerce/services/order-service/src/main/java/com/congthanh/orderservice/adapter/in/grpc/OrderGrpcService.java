package com.congthanh.orderservice.adapter.in.grpc;

import com.congthanh.ecommerce.order.v1.OrderAddress;
import com.congthanh.ecommerce.order.v1.OrderServiceGrpc;
import com.congthanh.ecommerce.order.v1.PlaceOrderRequest;
import com.congthanh.ecommerce.order.v1.PlaceOrderResponse;
import com.congthanh.ecommerce.order.v1.PricedLine;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.Item;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.Priced;
import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.Address;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.PaymentMethod;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import org.springframework.grpc.server.service.GrpcService;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * gRPC nội bộ cho checkout-service: tạo đơn với giá đã chốt. Không đi qua gateway; mạng nội bộ
 * (NetworkPolicy) giới hạn chỉ checkout-service gọi được.
 */
@GrpcService
class OrderGrpcService extends OrderServiceGrpc.OrderServiceImplBase {

    private final PlaceOrderUseCase placeOrder;

    OrderGrpcService(PlaceOrderUseCase placeOrder) {
        this.placeOrder = placeOrder;
    }

    @Override
    public void placeOrder(PlaceOrderRequest req, StreamObserver<PlaceOrderResponse> out) {
        try {
            if (req.getOrderId().isBlank() || req.getCustomerId().isBlank() || req.getLinesCount() == 0) {
                throw OrderException.invalid("orderId, customerId and lines are required");
            }
            Map<String, Money> prices = new LinkedHashMap<>();
            var items = new java.util.ArrayList<Item>();
            for (PricedLine l : req.getLinesList()) {
                prices.put(l.getProductId(), money(l.getUnitPrice()));
                items.add(new Item(l.getProductId(), l.getQuantity()));
            }
            String currency = prices.values().iterator().next().currency();
            var placed = placeOrder.place(new PlaceOrderCommand(
                    req.getCustomerId(), items, blankToNull(req.getPromotionCode()), paymentMethod(req.getPaymentMethod()),
                    blankToNull(req.getPaymentToken()), blankToNull(req.getReturnUrl()), blankToNull(req.getClientIp()),
                    address(req.getShippingAddress()),
                    req.hasBillingAddress() ? address(req.getBillingAddress()) : null,
                    blankToNull(req.getNote()),
                    new Priced(req.getOrderId(), prices,
                            req.hasShippingFee() ? money(req.getShippingFee()) : Money.zero(currency),
                            req.hasTax() ? money(req.getTax()) : Money.zero(currency))));
            out.onNext(PlaceOrderResponse.newBuilder()
                    .setOrderId(placed.orderId()).setOrderCode(placed.orderCode()).setStatus(placed.status().name())
                    .setTotal(proto(placed.total())).setDiscount(proto(placed.discount()))
                    .build());
            out.onCompleted();
        } catch (OrderException e) {
            Status s = switch (e.kind()) {
                case INVALID -> Status.INVALID_ARGUMENT;
                case NOT_FOUND -> Status.NOT_FOUND;
                case CONFLICT -> Status.ALREADY_EXISTS;
                case REJECTED -> Status.FAILED_PRECONDITION;
                case FORBIDDEN -> Status.PERMISSION_DENIED;
                case UNAVAILABLE -> Status.UNAVAILABLE;
            };
            // Mã lỗi nghiệp vụ (OUT_OF_STOCK, PROMOTION_...) đặt đầu mô tả để checkout-service hiển thị đúng.
            String code = e.code() == null ? "" : e.code() + ": ";
            out.onError(s.withDescription(code + e.getMessage()).asRuntimeException());
        } catch (IllegalArgumentException e) {
            out.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        }
    }

    static PaymentMethod paymentMethod(String v) {
        return switch (v.toUpperCase(java.util.Locale.ROOT)) {
            case "CARD", "CREDIT_CARD", "STRIPE" -> PaymentMethod.CARD;
            default -> PaymentMethod.valueOf(v.toUpperCase(java.util.Locale.ROOT));
        };
    }

    static Address address(OrderAddress a) {
        return new Address(a.getFullName(), a.getPhone(), a.getLine1(), blankToNull(a.getLine2()), blankToNull(a.getWard()),
                blankToNull(a.getDistrict()), a.getCity(), a.getCountryCode(), blankToNull(a.getPostalCode()));
    }

    static Money money(com.congthanh.ecommerce.common.v1.Money m) {
        BigDecimal amount = BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9)).stripTrailingZeros();
        if (amount.scale() < 0) {
            amount = amount.setScale(0);
        }
        return new Money(amount, m.getCurrencyCode());
    }

    static com.congthanh.ecommerce.common.v1.Money proto(Money m) {
        BigDecimal amount = m.amount().setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = amount.setScale(0, RoundingMode.DOWN);
        return com.congthanh.ecommerce.common.v1.Money.newBuilder().setCurrencyCode(m.currency())
                .setUnits(units.longValueExact()).setNanos(amount.subtract(units).movePointRight(9).intValueExact()).build();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
