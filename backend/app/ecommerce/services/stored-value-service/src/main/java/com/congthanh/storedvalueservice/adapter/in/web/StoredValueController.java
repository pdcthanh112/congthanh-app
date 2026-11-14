package com.congthanh.storedvalueservice.adapter.in.web;

import com.congthanh.storedvalueservice.application.port.in.StoredValueUseCases;
import com.congthanh.storedvalueservice.domain.exception.StoredValueException;
import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Movement;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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

@RestController
@RequestMapping("/api/ecommerce/stored-value")
@RequiredArgsConstructor
class StoredValueController {

    private final StoredValueUseCases storedValue;

    @GetMapping("/me")
    List<AccountDto> mine(@AuthenticationPrincipal Jwt jwt) {
        return storedValue.accounts(jwt.getSubject()).stream().map(AccountDto::of).toList();
    }

    @GetMapping("/accounts/{id}/history")
    List<MovementDto> history(@PathVariable String id, @RequestParam(defaultValue = "100") int limit,
                              @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        List<Movement> h = storedValue.history(id, limit);
        boolean staff = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                || a.getAuthority().equals("ROLE_SUPPORT") || a.getAuthority().equals("ROLE_FINANCE"));
        if (!staff && h.stream().anyMatch(m -> !m.customerId().equals(jwt.getSubject()))) {
            throw StoredValueException.notFound("account " + id);
        }
        return h.stream().map(MovementDto::of).toList();
    }

    // ---- CSKH / tài chính ----

    @GetMapping("/customers/{customerId}")
    List<AccountDto> ofCustomer(@PathVariable String customerId) {
        return storedValue.accounts(customerId).stream().map(AccountDto::of).toList();
    }

    /** Cộng store credit (bồi thường, điều chỉnh). reference là khoá idempotency, ví dụ mã ticket. */
    @PostMapping("/credits")
    @ResponseStatus(HttpStatus.CREATED)
    MovementDto credit(@Valid @RequestBody CreditRequest req) {
        return MovementDto.of(storedValue.credit(req.customerId(), req.amount(), req.currency(), req.reason(), req.reference()));
    }

    @PostMapping("/accounts/{id}/freeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void freeze(@PathVariable String id) {
        storedValue.setFrozen(id, true);
    }

    @PostMapping("/accounts/{id}/unfreeze")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unfreeze(@PathVariable String id) {
        storedValue.setFrozen(id, false);
    }

    record CreditRequest(@NotBlank String customerId, @NotNull @Positive BigDecimal amount, @NotBlank String currency,
                         @NotBlank String reason, @NotBlank String reference) {
    }

    record AccountDto(String id, String kind, String currency, BigDecimal balance, BigDecimal available, String status) {
        static AccountDto of(Account a) {
            return new AccountDto(a.id(), a.kind().name(), a.currency(), a.balance(), a.available(), a.status().name());
        }
    }

    record MovementDto(String accountId, String type, BigDecimal amount, String currency, String reason,
                       String reference, String orderId, BigDecimal balanceAfter, Instant at) {
        static MovementDto of(Movement m) {
            return new MovementDto(m.accountId(), m.type().name(), m.amount(), m.currency(), m.reason(), m.reference(),
                    m.orderId(), m.balanceAfter(), m.at());
        }
    }
}
