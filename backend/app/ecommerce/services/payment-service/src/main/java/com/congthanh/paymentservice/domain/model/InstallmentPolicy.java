package com.congthanh.paymentservice.domain.model;

import com.congthanh.paymentservice.domain.exception.PaymentException;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Luật cho phương thức trả góp (ADR 0016): đối tác nào đang bật, tiền tệ nào, khoảng số tiền cho phép
 * và authorization của đối tác giữ được bao lâu. Số liệu đến từ cấu hình {@code payment.installments}.
 * Không tính lịch trả hay APR: đối tác hiển thị điều khoản (quy định quảng cáo Reg Z).
 */
public final class InstallmentPolicy {

    public record Limit(BigDecimal minAmount, BigDecimal maxAmount, Duration authorizationTtl) {
        public Limit {
            if (minAmount == null || maxAmount == null || minAmount.compareTo(maxAmount) > 0) {
                throw new IllegalArgumentException("installment limit needs minAmount <= maxAmount");
            }
        }

        boolean accepts(BigDecimal amount) {
            return amount.compareTo(minAmount) >= 0 && amount.compareTo(maxAmount) <= 0;
        }
    }

    public record Option(PaymentMethod method, BigDecimal minAmount, BigDecimal maxAmount, String currency) {
    }

    private final Map<PaymentMethod, Limit> enabled;
    private final Set<String> currencies;

    public InstallmentPolicy(Map<PaymentMethod, Limit> enabled, Set<String> currencies) {
        enabled.keySet().forEach(m -> {
            if (!m.isInstallment()) {
                throw new IllegalArgumentException(m + " is not an installment method");
            }
        });
        this.enabled = enabled.isEmpty() ? Map.of() : new EnumMap<>(enabled);
        this.currencies = currencies.stream().map(c -> c.toUpperCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public static InstallmentPolicy disabled() {
        return new InstallmentPolicy(Map.of(), Set.of());
    }

    /** Đối tác dùng được cho số tiền này, lọc thêm theo cổng thực sự có cấu hình. */
    public List<Option> options(Money amount, Predicate<PaymentMethod> available) {
        List<Option> out = new ArrayList<>();
        if (!currencies.contains(amount.currency())) {
            return out;
        }
        enabled.forEach((method, limit) -> {
            if (limit.accepts(amount.amount()) && available.test(method)) {
                out.add(new Option(method, limit.minAmount(), limit.maxAmount(), amount.currency()));
            }
        });
        return out;
    }

    /** Ném UNSUPPORTED nếu đối tác tắt, sai tiền tệ hoặc số tiền ngoài khoảng; INVALID nếu thiếu returnUrl. */
    public void check(PaymentMethod method, Money amount, String returnUrl) {
        if (!method.isInstallment()) {
            return;
        }
        Limit limit = enabled.get(method);
        if (limit == null) {
            throw PaymentException.unsupported("payment method " + method + " is not enabled");
        }
        if (!currencies.contains(amount.currency())) {
            throw PaymentException.unsupported(method + " is not available for " + amount.currency());
        }
        if (!limit.accepts(amount.amount())) {
            throw PaymentException.unsupported(method + " accepts " + limit.minAmount() + "-" + limit.maxAmount()
                    + " " + amount.currency());
        }
        if (returnUrl == null || returnUrl.isBlank()) {
            // Khách luôn được chuyển sang trang đối tác để duyệt rồi quay về.
            throw PaymentException.invalid(method + " requires returnUrl");
        }
    }

    /** Hạn capture cho authorization vừa có; null nếu không phải trả góp hoặc không cấu hình. */
    public Duration authorizationTtl(PaymentMethod method) {
        Limit limit = enabled.get(method);
        return limit == null ? null : limit.authorizationTtl();
    }
}
