package com.congthanh.checkoutservice.adapter.out.grpc;

import com.congthanh.checkoutservice.domain.exception.CheckoutException;
import com.congthanh.checkoutservice.domain.model.Money;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Protos {

    private Protos() {
    }

    static com.congthanh.ecommerce.common.v1.Money toProto(Money m) {
        BigDecimal amount = m.amount().setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = amount.setScale(0, RoundingMode.DOWN);
        return com.congthanh.ecommerce.common.v1.Money.newBuilder()
                .setCurrencyCode(m.currency())
                .setUnits(units.longValueExact())
                .setNanos(amount.subtract(units).movePointRight(9).intValueExact())
                .build();
    }

    static Money fromProto(com.congthanh.ecommerce.common.v1.Money m, String fallbackCurrency) {
        BigDecimal amount = BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9));
        return new Money(amount, m.getCurrencyCode().isEmpty() ? fallbackCurrency : m.getCurrencyCode());
    }

    /** INVALID_ARGUMENT/FAILED_PRECONDITION/NOT_FOUND là lỗi nghiệp vụ; còn lại là tạm thời (retry/circuit breaker). */
    static CheckoutException translate(String service, StatusRuntimeException e) {
        String desc = e.getStatus().getDescription() == null ? "" : e.getStatus().getDescription();
        String msg = service + ": " + desc;
        Status.Code code = e.getStatus().getCode();
        return switch (code) {
            case INVALID_ARGUMENT -> CheckoutException.invalid(msg);
            case NOT_FOUND -> CheckoutException.notFound(msg);
            case FAILED_PRECONDITION, ALREADY_EXISTS -> CheckoutException.rejected(businessCode(desc), msg);
            default -> CheckoutException.unavailable(msg);
        };
    }

    /** order-service đặt mã nghiệp vụ ở đầu mô tả: "OUT_OF_STOCK: ...". */
    static String businessCode(String desc) {
        int i = desc.indexOf(':');
        if (i > 0) {
            String head = desc.substring(0, i);
            if (head.matches("[A-Z][A-Z0-9_]*")) {
                return head;
            }
        }
        return "ORDER_REJECTED";
    }
}
