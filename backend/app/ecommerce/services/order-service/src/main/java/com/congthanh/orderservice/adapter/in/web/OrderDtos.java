package com.congthanh.orderservice.adapter.in.web;

import com.congthanh.orderservice.domain.model.Address;
import com.congthanh.orderservice.domain.model.Order;
import com.congthanh.orderservice.domain.model.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO HTTP. Client chỉ gửi productId + số lượng: giá luôn lấy từ product-service. */
final class OrderDtos {

    private OrderDtos() {
    }

    record CheckoutRequest(
            @NotEmpty @Size(max = 100) List<@Valid ItemRequest> items,
            @Size(max = 64) String promotionCode,
            @NotNull PaymentMethod paymentMethod,
            @Size(max = 255) String paymentToken,
            @Size(max = 2048) String returnUrl,
            @NotNull @Valid AddressDto shippingAddress,
            @Valid AddressDto billingAddress,
            @Size(max = 1000) String note) {
    }

    record ItemRequest(@NotBlank String productId, @Positive @Max(999) int quantity) {
    }

    record AddressDto(@NotBlank String fullName, @NotBlank String phone, @NotBlank String line1, String line2,
                      String ward, String district, @NotBlank String city, @NotBlank String country,
                      String postalCode) {

        Address toDomain() {
            return new Address(fullName, phone, line1, line2, ward, district, city, country, postalCode);
        }

        static AddressDto of(Address a) {
            return a == null ? null : new AddressDto(a.fullName(), a.phone(), a.line1(), a.line2(), a.ward(),
                    a.district(), a.city(), a.country(), a.postalCode());
        }
    }

    record MoneyDto(BigDecimal amount, String currency) {
        static MoneyDto of(com.congthanh.orderservice.domain.model.Money m) {
            return new MoneyDto(m.amount(), m.currency());
        }
    }

    /** 202: đơn đã ghi nhận, saga đang chạy; theo dõi ở {@code checkoutUrl}. */
    record CheckoutAccepted(String orderId, String orderCode, String status, MoneyDto total, MoneyDto discount,
                            String checkoutUrl) {
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record OrderLineDto(String productId, String sku, String name, int quantity, MoneyDto unitPrice, MoneyDto total) {
    }

    record OrderDto(String orderId, String orderCode, String status, List<OrderLineDto> lines, MoneyDto subtotal,
                    MoneyDto discount, MoneyDto total, String promotionCode, String paymentMethod,
                    AddressDto shippingAddress, AddressDto billingAddress, String note, String cancelReason,
                    Instant placedAt, Instant updatedAt) {

        static OrderDto of(Order o) {
            return new OrderDto(o.id(), o.orderCode(), o.status().name(),
                    o.lines().stream().map(l -> new OrderLineDto(l.productId(), l.sku(), l.name(), l.quantity(),
                            MoneyDto.of(l.unitPrice()), MoneyDto.of(l.total()))).toList(),
                    MoneyDto.of(o.subtotal()), MoneyDto.of(o.discount()), MoneyDto.of(o.total()), o.promotionCode(),
                    o.checkout().paymentMethod().name(), AddressDto.of(o.shippingAddress()),
                    AddressDto.of(o.billingAddress()), o.note(), o.cancelReason(), o.placedAt(), o.updatedAt());
        }
    }

    record OrderSummaryDto(String orderCode, String status, MoneyDto total, int itemCount, Instant placedAt) {
        static OrderSummaryDto of(Order o) {
            return new OrderSummaryDto(o.orderCode(), o.status().name(), MoneyDto.of(o.total()),
                    o.lines().stream().mapToInt(com.congthanh.orderservice.domain.model.OrderLine::quantity).sum(),
                    o.placedAt());
        }
    }
}
