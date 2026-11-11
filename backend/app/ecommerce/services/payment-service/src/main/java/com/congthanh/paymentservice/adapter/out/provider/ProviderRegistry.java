package com.congthanh.paymentservice.adapter.out.provider;

import com.congthanh.paymentservice.application.port.out.PaymentProvider;
import com.congthanh.paymentservice.application.port.out.PaymentProviders;
import com.congthanh.paymentservice.domain.exception.PaymentException;
import com.congthanh.paymentservice.domain.model.PaymentMethod;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Chọn cổng cho phương thức: cổng thật đã cấu hình được ưu tiên, sandbox (nếu bật) lấp chỗ còn lại.
 * Cổng chỉ được tạo bean khi có cấu hình (xem các @ConditionalOnProperty).
 */
@Component
class ProviderRegistry implements PaymentProviders {

    private final Map<PaymentMethod, PaymentProvider> byMethod = new EnumMap<>(PaymentMethod.class);
    private final Map<String, PaymentProvider> byName;

    ProviderRegistry(List<PaymentProvider> providers) {
        for (PaymentProvider p : providers) {
            if (p instanceof SandboxProvider) {
                continue;
            }
            p.methods().forEach(m -> byMethod.putIfAbsent(m, p));
        }
        providers.stream().filter(p -> p instanceof SandboxProvider)
                .forEach(s -> s.methods().forEach(m -> byMethod.putIfAbsent(m, s)));
        this.byName = providers.stream().collect(Collectors.toMap(PaymentProvider::name, p -> p));
    }

    @Override
    public PaymentProvider forMethod(PaymentMethod method) {
        PaymentProvider p = byMethod.get(method);
        if (p == null) {
            throw PaymentException.unsupported("payment method " + method + " is not available");
        }
        return p;
    }

    @Override
    public PaymentProvider byName(String name) {
        PaymentProvider p = byName.get(name);
        if (p == null) {
            throw PaymentException.unavailable("payment provider " + name + " is not configured");
        }
        return p;
    }

    @Override
    public boolean supports(PaymentMethod method) {
        return byMethod.containsKey(method);
    }

    Set<PaymentMethod> availableMethods() {
        return byMethod.keySet();
    }
}
