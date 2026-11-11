package com.congthanh.checkoutservice.adapter.in.web;

import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.CancelRequest;
import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.ItemRequest;
import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.OpenRequest;
import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.PlaceRequest;
import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.SessionDto;
import com.congthanh.checkoutservice.adapter.in.web.CheckoutDtos.UpdateRequest;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.Customer;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.OpenCommand;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.Outcome;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.PlaceCommand;
import com.congthanh.checkoutservice.application.port.in.CheckoutUseCase.UpdateCommand;
import com.congthanh.checkoutservice.domain.model.Money;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecommerce/checkout")
@Tag(name = "Checkout", description = "Checkout sessions: quote, place, track")
class CheckoutController {

    private final CheckoutUseCase checkout;
    private final PlaceLimiter placeLimiter;

    CheckoutController(CheckoutUseCase checkout, PlaceLimiter placeLimiter) {
        this.checkout = checkout;
        this.placeLimiter = placeLimiter;
    }

    @Operation(summary = "Open a checkout session and quote it",
            description = "Prices come from pricing-service (customer group from the token), plus voucher, shipping and tax "
                    + "when an address is given. The session expires after a period of inactivity.")
    @PostMapping("/sessions")
    ResponseEntity<SessionDto> open(@Valid @RequestBody OpenRequest body, @AuthenticationPrincipal Jwt jwt) {
        var s = checkout.open(customer(jwt), new OpenCommand(
                body.items().stream().map(ItemRequest::toDomain).toList(), body.currency(), body.promotionCode(),
                body.shippingAddress() == null ? null : body.shippingAddress().toDomain(),
                body.billingAddress() == null ? null : body.billingAddress().toDomain(), body.note()));
        return ResponseEntity.created(URI.create("/api/ecommerce/checkout/sessions/" + s.id())).body(SessionDto.of(s, null));
    }

    @Operation(summary = "Checkout session with the latest quote and, once placed, the saga progress")
    @GetMapping("/sessions/{id}")
    SessionDto get(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        var v = checkout.get(customer(jwt), id);
        return SessionDto.of(v.session(), v.progress());
    }

    @Operation(summary = "Change items, voucher, addresses or shipping option and re-quote")
    @PatchMapping("/sessions/{id}")
    SessionDto update(@PathVariable String id, @Valid @RequestBody UpdateRequest body, @AuthenticationPrincipal Jwt jwt) {
        var s = checkout.update(customer(jwt), id, new UpdateCommand(
                body.items() == null ? null : body.items().stream().map(ItemRequest::toDomain).toList(),
                body.promotionCode(), Boolean.TRUE.equals(body.removePromotion()),
                body.shippingAddress() == null ? null : body.shippingAddress().toDomain(),
                body.billingAddress() == null ? null : body.billingAddress().toDomain(),
                body.shipping() == null ? null : body.shipping().carrier(),
                body.shipping() == null ? null : body.shipping().serviceCode(), body.note()));
        return SessionDto.of(s, null);
    }

    @Operation(summary = "Place the order",
            description = "Re-quotes, runs the risk check and creates the order. 201 = order placed (poll the session for "
                    + "the payment redirect URL); 202 = held for manual review. Send the same Idempotency-Key to retry safely.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
    @PostMapping("/sessions/{id}/place")
    ResponseEntity<SessionDto> place(@PathVariable String id, @Valid @RequestBody PlaceRequest body,
                                     @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        Customer c = customer(jwt);
        var current = checkout.get(c, id).session();
        Money expected = body.expectedTotal() == null ? null : new Money(body.expectedTotal(), current.currency());
        var placement = placeLimiter.run(() -> checkout.place(c, id, new PlaceCommand(body.paymentMethod(),
                body.paymentToken(), body.returnUrl(), clientIp(http), body.deviceId(), expected)));
        HttpStatus status = placement.outcome() == Outcome.PLACED ? HttpStatus.CREATED : HttpStatus.ACCEPTED;
        return ResponseEntity.status(status).body(SessionDto.of(placement.session(), null));
    }

    @Operation(summary = "Cancel the session (or ask the saga to cancel the order if it is not paid yet)")
    @PostMapping("/sessions/{id}/cancel")
    ResponseEntity<SessionDto> cancel(@PathVariable String id, @RequestBody(required = false) CancelRequest body,
                                      @AuthenticationPrincipal Jwt jwt) {
        var s = checkout.cancel(customer(jwt), id, body == null ? null : body.reason());
        return ResponseEntity.accepted().body(SessionDto.of(s, null));
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Khách từ JWT Keycloak: vai trò realm làm nhóm khách cho bảng giá (VIP, WHOLESALE...), email và thời điểm
     * tạo tài khoản (claim "created_at" nếu realm có mapper) cho fraud-service.
     */
    static Customer customer(Jwt jwt) {
        List<String> roles = new ArrayList<>();
        if (jwt.getClaims().get("realm_access") instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> rs) {
            rs.forEach(r -> roles.add(String.valueOf(r).toUpperCase(java.util.Locale.ROOT)));
        }
        boolean staff = roles.contains("ADMIN") || roles.contains("SUPPORT");
        Instant created = null;
        Object c = jwt.getClaims().get("created_at");
        if (c instanceof Number n) {
            long v = n.longValue();
            created = v > 100_000_000_000L ? Instant.ofEpochMilli(v) : Instant.ofEpochSecond(v);
        }
        return new Customer(jwt.getSubject(), List.copyOf(roles), jwt.getClaimAsString("email"), created, staff);
    }
}
