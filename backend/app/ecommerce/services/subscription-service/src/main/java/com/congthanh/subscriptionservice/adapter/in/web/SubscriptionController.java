package com.congthanh.subscriptionservice.adapter.in.web;

import com.congthanh.subscriptionservice.application.port.in.SubscriptionUseCases;
import com.congthanh.subscriptionservice.domain.model.Line;
import com.congthanh.subscriptionservice.domain.model.Subscription;
import com.congthanh.subscriptionservice.domain.model.SubscriptionStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/ecommerce/subscriptions")
@RequiredArgsConstructor
class SubscriptionController {

    private final SubscriptionUseCases subs;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    Dto create(@Valid @RequestBody CreateRequest r, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.create(jwt.getSubject(), lines(r.lines()), r.intervalDays(), r.firstDeliveryDate(),
                r.paymentMethodRef(), r.currency(), r.addressId()));
    }

    @GetMapping("/mine")
    List<Dto> mine(@AuthenticationPrincipal Jwt jwt) {
        return subs.mine(jwt.getSubject()).stream().map(Dto::of).toList();
    }

    @GetMapping("/{id}")
    Dto get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPPORT"));
        return Dto.of(subs.get(id, jwt.getSubject(), staff));
    }

    @PatchMapping("/{id}")
    Dto change(@PathVariable String id, @RequestBody ChangeRequest r, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.change(id, jwt.getSubject(), r.lines() == null ? null : lines(r.lines()), r.intervalDays()));
    }

    @PostMapping("/{id}/skip")
    Dto skip(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.skip(id, jwt.getSubject()));
    }

    @PostMapping("/{id}/pause")
    Dto pause(@PathVariable String id, @RequestBody(required = false) PauseRequest r, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.pause(id, jwt.getSubject(), r == null ? null : r.until()));
    }

    @PostMapping("/{id}/resume")
    Dto resume(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.resume(id, jwt.getSubject()));
    }

    /** Huỷ ngay, không cần gọi điện hay qua nhiều bước (FTC click-to-cancel). */
    @PostMapping("/{id}/cancel")
    Dto cancel(@PathVariable String id, @RequestBody(required = false) CancelRequest r, @AuthenticationPrincipal Jwt jwt) {
        return Dto.of(subs.cancel(id, jwt.getSubject(), r == null ? null : r.reason()));
    }

    @GetMapping
    List<Dto> byStatus(@RequestParam SubscriptionStatus status, @RequestParam(defaultValue = "100") int limit) {
        return subs.byStatus(status, limit).stream().map(Dto::of).toList();
    }

    private static List<Line> lines(List<LineRequest> r) {
        return r.stream().map(l -> new Line(l.productId(), l.quantity())).toList();
    }

    record LineRequest(@NotBlank String productId, @Positive int quantity) {
    }

    record CreateRequest(@NotEmpty List<@Valid LineRequest> lines, @Positive int intervalDays,
                         @NotNull LocalDate firstDeliveryDate, @NotBlank String paymentMethodRef,
                         @NotBlank String currency, String addressId) {
    }

    record ChangeRequest(List<LineRequest> lines, Integer intervalDays) {
    }

    record PauseRequest(LocalDate until) {
    }

    record CancelRequest(String reason) {
    }

    record Dto(String id, String status, String currency, List<Line> lines, int intervalDays,
               LocalDate nextDeliveryDate, int cycle,
               LocalDate pausedUntil, String cancelReason) {
        static Dto of(Subscription s) {
            return new Dto(s.id(), s.status().name(), s.currency(), s.lines(), s.intervalDays(), s.nextDeliveryDate(), s.cycle(),
                    s.pausedUntil(), s.cancelReason());
        }
    }
}
