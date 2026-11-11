package com.congthanh.checkoutservice.adapter.in.web;

import com.congthanh.checkoutservice.application.port.out.CheckoutWorkflows.Progress;
import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.Money;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** DTO HTTP. Client chỉ gửi productId + số lượng: giá luôn do pricing-service quyết định. */
final class CheckoutDtos {

    private CheckoutDtos() {
    }

    record ItemRequest(@NotBlank String productId, @Positive @Max(999) int quantity) {
        CheckoutSession.Item toDomain() {
            return new CheckoutSession.Item(productId, quantity);
        }
    }

    record AddressDto(@NotBlank String fullName, @NotBlank String phone, @NotBlank String line1, String line2,
                      String ward, String district, @NotBlank String city, @NotBlank @Size(min = 2, max = 2) String country,
                      String postalCode) {

        Address toDomain() {
            return new Address(fullName, phone, line1, line2, ward, district, city, country, postalCode);
        }

        static AddressDto of(Address a) {
            return a == null ? null : new AddressDto(a.fullName(), a.phone(), a.line1(), a.line2(), a.ward(),
                    a.district(), a.city(), a.country(), a.postalCode());
        }
    }

    record OpenRequest(@NotEmpty @Size(max = 100) List<@Valid ItemRequest> items, @Size(min = 3, max = 3) String currency,
                       @Size(max = 64) String promotionCode, @Valid AddressDto shippingAddress,
                       @Valid AddressDto billingAddress, @Size(max = 1000) String note) {
    }

    record ShippingChoice(@NotBlank String carrier, @NotBlank String serviceCode) {
    }

    record UpdateRequest(@Size(max = 100) List<@Valid ItemRequest> items, @Size(max = 64) String promotionCode,
                         Boolean removePromotion, @Valid AddressDto shippingAddress, @Valid AddressDto billingAddress,
                         @Valid ShippingChoice shipping, @Size(max = 1000) String note) {
    }

    record MoneyDto(BigDecimal amount, String currency) {
        static MoneyDto of(Money m) {
            return m == null ? null : new MoneyDto(m.amount(), m.currency());
        }
    }

    record PlaceRequest(@NotBlank @Size(max = 32) String paymentMethod, @Size(max = 255) String paymentToken,
                        @Size(max = 2048) String returnUrl, @Size(max = 128) String deviceId,
                        /** Tổng tiền khách đang thấy; khác tổng mới tính lại → 409 PRICE_CHANGED. */
                        BigDecimal expectedTotal) {
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record LineDto(String productId, String sku, String name, int quantity, MoneyDto unitPrice, MoneyDto compareAt,
                   MoneyDto lineTotal) {
    }

    record ShippingOptionDto(String carrier, String serviceCode, String serviceName, MoneyDto fee, int estimatedDaysMin,
                             int estimatedDaysMax, boolean selected) {
    }

    record TotalsDto(MoneyDto subtotal, MoneyDto discount, MoneyDto shippingFee, MoneyDto tax, MoneyDto total) {
    }

    record ProgressDto(String step, String paymentRedirectUrl, String failureReason) {
        static ProgressDto of(Progress p) {
            return p == null ? null : new ProgressDto(p.step(), p.paymentRedirectUrl(), p.failureReason());
        }
    }

    /** Có khi phiên được mở cho một suất flash sale: không đổi được sản phẩm, phải đặt trước {@code expiresAt}. */
    record FlashSaleDto(String saleId, String claimId, Instant expiresAt) {
        static FlashSaleDto of(CheckoutSession.FlashSale f) {
            return f == null ? null : new FlashSaleDto(f.saleId(), f.claimId(), f.expiresAt());
        }
    }

    record SessionDto(String id, String status, String currency, List<LineDto> lines, List<String> unpricedProductIds,
                      String promotionCode, String promotionError, AddressDto shippingAddress, AddressDto billingAddress,
                      List<ShippingOptionDto> shippingOptions, TotalsDto totals, String note, String orderId,
                      String orderCode, String failureReason, Instant expiresAt, ProgressDto progress,
                      FlashSaleDto flashSale) {

        static SessionDto of(CheckoutSession s, Progress progress) {
            var t = s.totals();
            String selected = s.selectedShipping();
            return new SessionDto(s.id(), s.status().name(), s.currency(),
                    s.lines().stream().map(l -> new LineDto(l.productId(), l.sku(), l.name(), l.quantity(),
                            MoneyDto.of(l.unitPrice()), MoneyDto.of(l.compareAt()), MoneyDto.of(l.lineTotal()))).toList(),
                    s.unpriced(), s.promotionCode(), s.promotionError(), AddressDto.of(s.shippingAddress()),
                    AddressDto.of(s.billingAddress()),
                    s.shippingOptions().stream().map(o -> new ShippingOptionDto(o.carrier(), o.serviceCode(),
                            o.serviceName(), MoneyDto.of(o.fee()), o.daysMin(), o.daysMax(), o.key().equals(selected))).toList(),
                    new TotalsDto(MoneyDto.of(t.subtotal()), MoneyDto.of(t.discount()), MoneyDto.of(t.shippingFee()),
                            MoneyDto.of(t.tax()), MoneyDto.of(t.total())),
                    s.note(),
                    // Order id chỉ lộ ra khi đơn đã tạo.
                    s.orderCode() == null ? null : s.orderId(), s.orderCode(), s.failureReason(), s.expiresAt(),
                    ProgressDto.of(progress), FlashSaleDto.of(s.flashSale()));
        }
    }
}
