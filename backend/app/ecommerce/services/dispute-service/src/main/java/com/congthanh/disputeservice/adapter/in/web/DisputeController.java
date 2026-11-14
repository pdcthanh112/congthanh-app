package com.congthanh.disputeservice.adapter.in.web;

import com.congthanh.disputeservice.application.port.in.DisputeDesk;
import com.congthanh.disputeservice.application.port.in.DisputeIntake;
import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeStatus;
import com.congthanh.disputeservice.domain.model.Evidence;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ecommerce/disputes")
@RequiredArgsConstructor
class DisputeController {

    private final DisputeDesk desk;
    private final DisputeIntake intake;

    @GetMapping
    List<DisputeDto> byStatus(@RequestParam(defaultValue = "NEEDS_RESPONSE") DisputeStatus status,
                           @RequestParam(defaultValue = "100") int limit) {
        return desk.byStatus(status, limit).stream().map(DisputeDto::of).toList();
    }

    @GetMapping("/due-soon")
    List<DisputeDto> dueSoon() {
        return desk.dueSoon().stream().map(DisputeDto::of).toList();
    }

    @GetMapping("/{id}")
    DisputeDto get(@PathVariable String id) {
        return DisputeDto.of(desk.get(id));
    }

    /** Nhập tay tranh chấp của cổng chưa có webhook (PayPal) hoặc để thử ở sandbox. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    DisputeDto create(@Valid @RequestBody ManualDispute req) {
        return DisputeDto.of(intake.receive(new DisputeIntake.ProviderDispute(req.provider(), req.providerDisputeId(),
                req.providerPaymentRef(), req.amount(), req.fee(), req.currency(), req.reason(), req.evidenceDueBy(),
                "needs_response")));
    }

    @PostMapping("/{id}/refresh-evidence")
    DisputeDto refresh(@PathVariable String id) {
        return DisputeDto.of(desk.refreshEvidence(id));
    }

    @PostMapping("/{id}/submit")
    DisputeDto submit(@PathVariable String id, @RequestBody(required = false) SubmitRequest req, @AuthenticationPrincipal Jwt jwt) {
        return DisputeDto.of(desk.submit(id, req == null ? Map.of() : req.fields(), jwt.getSubject()));
    }

    @PostMapping("/{id}/accept")
    DisputeDto accept(@PathVariable String id, @Valid @RequestBody ReasonRequest req, @AuthenticationPrincipal Jwt jwt) {
        return DisputeDto.of(desk.accept(id, req.reason(), jwt.getSubject()));
    }

    record ManualDispute(@NotBlank String provider, @NotBlank String providerDisputeId, String providerPaymentRef,
                         @NotNull @Positive BigDecimal amount, BigDecimal fee, @NotBlank String currency,
                         String reason, Instant evidenceDueBy) {
    }

    record DisputeDto(String id, String provider, String providerDisputeId, String paymentId, String orderId,
                      String customerId, BigDecimal amount, BigDecimal fee, String currency, String reason,
                      Instant evidenceDueBy, String status, Evidence evidence, String note, Instant createdAt,
                      Instant updatedAt) {
        static DisputeDto of(Dispute d) {
            return new DisputeDto(d.id(), d.provider(), d.providerDisputeId(), d.paymentId(), d.orderId(),
                    d.customerId(), d.amount(), d.fee(), d.currency(), d.reason().name(), d.evidenceDueBy(),
                    d.status().name(), d.evidence(), d.note(), d.createdAt(), d.updatedAt());
        }
    }

    record SubmitRequest(Map<String, String> fields) {
    }

    record ReasonRequest(@NotBlank String reason) {
    }
}
