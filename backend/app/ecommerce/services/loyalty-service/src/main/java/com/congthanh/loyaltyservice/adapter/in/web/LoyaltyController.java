package com.congthanh.loyaltyservice.adapter.in.web;

import com.congthanh.loyaltyservice.application.port.in.LoyaltyUseCases;
import com.congthanh.loyaltyservice.domain.model.Lot;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/ecommerce/loyalty")
@RequiredArgsConstructor
class LoyaltyController {

    private final LoyaltyUseCases loyalty;

    @GetMapping("/me")
    SummaryDto me(@AuthenticationPrincipal Jwt jwt) {
        return SummaryDto.of(loyalty.summary(jwt.getSubject()));
    }

    @GetMapping("/customers/{customerId}")
    SummaryDto ofCustomer(@PathVariable String customerId) {
        return SummaryDto.of(loyalty.summary(customerId));
    }

    record LotDto(String orderId, long points, long remaining, String status, Instant earnedAt, Instant confirmAt,
                  Instant expiresAt) {
        static LotDto of(Lot l) {
            return new LotDto(l.orderId(), l.points(), l.remaining(), l.status().name(), l.earnedAt(), l.confirmAt(),
                    l.expiresAt());
        }
    }

    record SummaryDto(String customerId, String tier, long available, long pending, BigDecimal availableValueUsd,
                      List<LotDto> lots) {
        static SummaryDto of(LoyaltyUseCases.Summary s) {
            return new SummaryDto(s.customerId(), s.tier().name(), s.available(), s.pending(), s.availableValueUsd(),
                    s.lots().stream().map(LotDto::of).toList());
        }
    }
}
