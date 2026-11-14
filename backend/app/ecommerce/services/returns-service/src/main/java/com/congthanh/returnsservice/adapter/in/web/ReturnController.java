package com.congthanh.returnsservice.adapter.in.web;

import com.congthanh.returnsservice.application.port.in.CustomerReturns;
import com.congthanh.returnsservice.application.port.in.ReturnAdmin;
import com.congthanh.returnsservice.domain.exception.ReturnException;
import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.congthanh.returnsservice.domain.model.RefundMethod;
import com.congthanh.returnsservice.domain.model.ReturnLine;
import com.congthanh.returnsservice.domain.model.ReturnReason;
import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ecommerce/returns")
@RequiredArgsConstructor
class ReturnController {

    private final CustomerReturns customer;
    private final ReturnAdmin admin;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ReturnDto open(@Valid @RequestBody OpenReturnRequest req, @AuthenticationPrincipal Jwt jwt) {
        List<ReturnLine> lines = req.lines().stream()
                .map(l -> ReturnLine.requested(l.productId(), l.quantity(), reason(l.reason())))
                .toList();
        RefundMethod method = req.refundMethod() == null || req.refundMethod().isBlank() ? RefundMethod.ORIGINAL_PAYMENT
                : parse(RefundMethod.class, req.refundMethod(), "refund method");
        return ReturnDto.of(customer.open(jwt.getSubject(), req.orderId(), lines, method));
    }

    @GetMapping("/mine")
    List<ReturnDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return customer.mine(jwt.getSubject()).stream().map(ReturnDto::of).toList();
    }

    @GetMapping("/{id}")
    ReturnDto get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        return ReturnDto.of(customer.get(id, jwt.getSubject(), isStaff(auth)));
    }

    @PostMapping("/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void cancel(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        customer.cancel(id, jwt.getSubject());
    }

    @GetMapping
    List<ReturnDto> byStatus(@RequestParam ReturnStatus status, @RequestParam(defaultValue = "100") int limit) {
        return admin.byStatus(status, limit).stream().map(ReturnDto::of).toList();
    }

    @PostMapping("/{id}/approve")
    ReturnDto approve(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ReturnDto.of(admin.approve(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/reject")
    ReturnDto reject(@PathVariable String id, @Valid @RequestBody ReasonRequest req, @AuthenticationPrincipal Jwt jwt) {
        return ReturnDto.of(admin.reject(id, jwt.getSubject(), req.reason()));
    }

    @PostMapping("/{id}/receive")
    ReturnDto receive(@PathVariable String id, @Valid @RequestBody ReceiveRequest req, @AuthenticationPrincipal Jwt jwt) {
        Map<String, ItemCondition> conditions = req.lines().stream().collect(Collectors.toMap(
                InspectedLine::productId, l -> condition(l.condition()), (a, b) -> {
                    throw ReturnException.invalid("duplicate inspection line");
                }));
        return ReturnDto.of(admin.receive(id, conditions, jwt.getSubject()));
    }

    @PostMapping("/{id}/retry-refund")
    ReturnDto retryRefund(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ReturnDto.of(admin.retryRefund(id, jwt.getSubject()));
    }

    private static boolean isStaff(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPPORT") || a.getAuthority().equals("ROLE_WAREHOUSE"));
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String s, String what) {
        try {
            return Enum.valueOf(type, s.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw ReturnException.invalid("unknown " + what + " " + s);
        }
    }

    private static ReturnReason reason(String s) {
        try {
            return ReturnReason.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw ReturnException.invalid("unknown return reason " + s);
        }
    }

    private static ItemCondition condition(String s) {
        try {
            return ItemCondition.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw ReturnException.invalid("unknown item condition " + s);
        }
    }

    record LineRequest(@NotBlank String productId, @Positive int quantity, @NotBlank String reason) {
    }

    record OpenReturnRequest(@NotBlank String orderId, @NotEmpty List<@Valid LineRequest> lines, String refundMethod) {
    }

    record InspectedLine(@NotBlank String productId, @NotBlank String condition) {
    }

    record ReceiveRequest(@NotEmpty List<@Valid InspectedLine> lines) {
    }

    record ReasonRequest(@NotBlank String reason) {
    }

    record LineDto(String productId, int quantity, String reason, BigDecimal unitPrice, String condition) {
    }

    record ReturnDto(String id, String rmaCode, String orderId, String customerId, String status, String refundMethod,
                     String currency,
                     BigDecimal refundAmount, BigDecimal restockingFee, String note, List<LineDto> lines,
                     Instant createdAt, Instant updatedAt) {
        static ReturnDto of(ReturnRequest r) {
            return new ReturnDto(r.id(), r.rmaCode(), r.orderId(), r.customerId(), r.status().name(),
                    r.refundMethod().name(), r.currency(),
                    r.refundAmount(), r.restockingFee(), r.note(),
                    r.lines().stream().map(l -> new LineDto(l.productId(), l.quantity(), l.reason().name(),
                            l.unitPrice(), l.condition() == null ? null : l.condition().name())).toList(),
                    r.createdAt(), r.updatedAt());
        }
    }
}
