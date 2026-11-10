package com.congthanh.orderservice.adapter.out.grpc;

import com.congthanh.orderservice.domain.exception.OrderException;
import com.congthanh.orderservice.domain.model.Money;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class Protos {

    private Protos() {
    }

    static com.congthanh.ecom.common.v1.Money toProto(Money m) {
        BigDecimal amount = m.amount().setScale(9, RoundingMode.HALF_EVEN);
        BigDecimal units = amount.setScale(0, RoundingMode.DOWN);
        return com.congthanh.ecom.common.v1.Money.newBuilder()
                .setCurrencyCode(m.currency())
                .setUnits(units.longValueExact())
                .setNanos(amount.subtract(units).movePointRight(9).intValueExact())
                .build();
    }

    static Money fromProto(com.congthanh.ecom.common.v1.Money m, String fallbackCurrency) {
        BigDecimal amount = BigDecimal.valueOf(m.getUnits()).add(BigDecimal.valueOf(m.getNanos(), 9)).stripTrailingZeros();
        if (amount.scale() < 0) {
            amount = amount.setScale(0);
        }
        String currency = m.getCurrencyCode().isEmpty() ? fallbackCurrency : m.getCurrencyCode();
        return new Money(amount, currency);
    }

    /**
     * Lỗi gRPC → lỗi miền: INVALID_ARGUMENT/FAILED_PRECONDITION là lỗi nghiệp vụ, còn lại là
     * dịch vụ tạm không dùng được (để retry/circuit breaker xử lý).
     */
    static OrderException translate(String service, StatusRuntimeException e) {
        Status.Code code = e.getStatus().getCode();
        String msg = service + ": " + e.getStatus().getDescription();
        return switch (code) {
            case INVALID_ARGUMENT -> OrderException.invalid(msg);
            case NOT_FOUND -> OrderException.notFound(msg);
            case FAILED_PRECONDITION, ALREADY_EXISTS -> OrderException.conflict(msg);
            default -> OrderException.unavailable(msg);
        };
    }
}
