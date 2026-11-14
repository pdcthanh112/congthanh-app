package com.congthanh.ledgerservice.adapter.in.web;

import com.congthanh.ledgerservice.application.port.in.LedgerQueries;
import com.congthanh.ledgerservice.application.port.in.ManualEntries;
import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.JournalEntry;
import com.congthanh.ledgerservice.domain.model.Posting;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
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
import java.time.LocalDate;
import java.util.List;

/** API cho team tài chính (role FINANCE hoặc ADMIN). Không có API sửa/xoá bút toán, chỉ đảo qua maker/checker. */
@RestController
@RequestMapping("/api/ecommerce/ledger")
@RequiredArgsConstructor
class LedgerController {

    private final LedgerQueries queries;
    private final ManualEntries manual;

    @GetMapping("/accounts/{code}/balance")
    List<BalanceDto> balance(@PathVariable String code) {
        return queries.balance(code).stream().map(BalanceDto::of).toList();
    }

    @GetMapping("/accounts/{code}/entries")
    List<EntryDto> accountEntries(@PathVariable String code,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(defaultValue = "100") int limit) {
        return queries.entriesByAccount(code, from, to, limit).stream().map(EntryDto::of).toList();
    }

    @GetMapping("/entries")
    List<EntryDto> entriesBySource(@RequestParam String sourceType, @RequestParam String sourceId) {
        return queries.entriesBySource(sourceType, sourceId).stream().map(EntryDto::of).toList();
    }

    @GetMapping("/trial-balance")
    TrialBalanceDto trialBalance(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        var tb = queries.trialBalance(asOf);
        return new TrialBalanceDto(tb.asOf(), tb.balanced(), tb.accounts().stream().map(BalanceDto::of).toList());
    }

    @GetMapping("/manual-entries")
    List<ManualEntries.ManualEntry> pending() {
        return manual.pending();
    }

    @PostMapping("/manual-entries")
    @ResponseStatus(HttpStatus.CREATED)
    ManualEntries.ManualEntry propose(@Valid @RequestBody ManualEntryRequest req, @AuthenticationPrincipal Jwt jwt) {
        List<Posting> postings = req.postings().stream()
                .map(p -> "DEBIT".equalsIgnoreCase(p.side())
                        ? Posting.debit(p.account(), p.amount(), p.currency())
                        : Posting.credit(p.account(), p.amount(), p.currency()))
                .toList();
        return manual.propose(req.description(), req.bookingDate(), postings, jwt.getSubject());
    }

    @PostMapping("/entries/{id}/reversal")
    @ResponseStatus(HttpStatus.CREATED)
    ManualEntries.ManualEntry proposeReversal(@PathVariable String id, @Valid @RequestBody ReasonRequest req,
                                              @AuthenticationPrincipal Jwt jwt) {
        return manual.proposeReversal(id, req.reason(), jwt.getSubject());
    }

    @PostMapping("/manual-entries/{id}/approve")
    ApprovedDto approve(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return new ApprovedDto(manual.approve(id, jwt.getSubject()));
    }

    @PostMapping("/manual-entries/{id}/reject")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void reject(@PathVariable String id, @Valid @RequestBody ReasonRequest req, @AuthenticationPrincipal Jwt jwt) {
        manual.reject(id, jwt.getSubject(), req.reason());
    }

    record PostingRequest(@NotBlank String account, @NotBlank String side, @NotNull @Positive BigDecimal amount,
                          @NotBlank String currency) {
    }

    record ManualEntryRequest(@NotBlank String description, LocalDate bookingDate,
                              @NotEmpty List<@Valid PostingRequest> postings) {
    }

    record ReasonRequest(@NotBlank String reason) {
    }

    record ApprovedDto(String journalEntryId) {
    }

    record BalanceDto(String account, String type, String currency, BigDecimal debits, BigDecimal credits,
                      BigDecimal balance) {
        static BalanceDto of(AccountBalance b) {
            return new BalanceDto(b.account().value(), b.account().type().name(), b.currency(), b.debits(),
                    b.credits(), b.balance());
        }
    }

    record PostingDto(String account, String side, BigDecimal amount, String currency) {
    }

    record EntryDto(String id, String sourceType, String sourceId, String description, LocalDate bookingDate,
                    String createdBy, List<PostingDto> postings) {
        static EntryDto of(JournalEntry e) {
            return new EntryDto(e.id(), e.sourceType(), e.sourceId(), e.description(), e.bookingDate(), e.createdBy(),
                    e.postings().stream().map(p -> new PostingDto(p.account().value(), p.side().name(), p.amount(),
                            p.currency())).toList());
        }
    }

    record TrialBalanceDto(LocalDate asOf, boolean balanced, List<BalanceDto> accounts) {
    }
}
