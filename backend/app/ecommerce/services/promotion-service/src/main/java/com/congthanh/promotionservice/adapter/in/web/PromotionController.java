package com.congthanh.promotionservice.adapter.in.web;

import com.congthanh.promotionservice.application.port.in.CheckoutDiscounts;
import com.congthanh.promotionservice.application.port.in.PromotionAdmin;
import com.congthanh.promotionservice.application.port.in.PromotionAdmin.PromotionSpec;
import com.congthanh.promotionservice.domain.model.DiscountRule;
import com.congthanh.promotionservice.domain.model.Promotion;
import com.congthanh.promotionservice.domain.model.PromotionType;
import com.congthanh.promotionservice.domain.model.Quote;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;

@RestController
@RequestMapping("/api/ecommerce/promotions")
@Tag(name = "Promotions")
class PromotionController {

    private final PromotionAdmin admin;
    private final CheckoutDiscounts discounts;

    PromotionController(PromotionAdmin admin, CheckoutDiscounts discounts) {
        this.admin = admin;
        this.discounts = discounts;
    }

    record PromotionRequest(@NotBlank @Size(max = 32) String code, @NotNull PromotionType type,
                            @NotNull DiscountRule.Kind discountKind, @NotNull @Positive BigDecimal discountValue,
                            @NotBlank String currency, @PositiveOrZero BigDecimal minSubtotal, BigDecimal maxDiscount,
                            @PositiveOrZero int usageLimit, @PositiveOrZero int perCustomerLimit,
                            Instant startsAt, Instant endsAt, @Size(max = 2000) String description) {
        PromotionSpec spec() {
            return new PromotionSpec(code, type, new DiscountRule(discountKind, discountValue, currency, minSubtotal, maxDiscount),
                    usageLimit, perCustomerLimit, startsAt, endsAt, description);
        }
    }

    record PromotionView(long id, String code, String type, String discountKind, BigDecimal discountValue,
                         String currency, BigDecimal minSubtotal, BigDecimal maxDiscount, int usageLimit,
                         int perCustomerLimit, int usedCount, Instant startsAt, Instant endsAt, boolean active,
                         String description) {
        static PromotionView of(Promotion p) {
            return new PromotionView(p.id(), p.code(), p.type().name(), p.rule().kind().name(), p.rule().value(),
                    p.rule().currency(), p.rule().minSubtotal(), p.rule().maxDiscount(), p.usageLimit(),
                    p.perCustomerLimit(), p.usedCount(), p.startsAt(), p.endsAt(), p.active(), p.description());
        }
    }

    @Operation(summary = "Look up a promotion by code")
    @GetMapping(params = "code")
    PromotionView byCode(@RequestParam String code) {
        return PromotionView.of(admin.byCode(code));
    }

    @GetMapping("/{id}")
    PromotionView byId(@PathVariable long id) {
        return PromotionView.of(admin.byId(id));
    }

    @Operation(summary = "Preview the discount for the current cart (no usage is consumed)")
    @GetMapping("/quote")
    Quote quote(@RequestParam String code, @RequestParam BigDecimal subtotal, @RequestParam String currency,
                Authentication auth) {
        return discounts.quote(code, auth == null ? null : auth.getName(), subtotal, currency.toUpperCase());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    PromotionView create(@Valid @RequestBody PromotionRequest body) {
        return PromotionView.of(admin.create(body.spec()));
    }

    @PutMapping("/{id}")
    PromotionView update(@PathVariable long id, @Valid @RequestBody PromotionRequest body) {
        return PromotionView.of(admin.update(id, body.spec()));
    }

    @PostMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deactivate(@PathVariable long id) {
        admin.deactivate(id);
    }
}
