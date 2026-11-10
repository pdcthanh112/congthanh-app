package com.congthanh.orderservice.adapter.in.web;

import com.congthanh.orderservice.adapter.in.web.OrderDtos.CancelRequest;
import com.congthanh.orderservice.adapter.in.web.OrderDtos.CheckoutAccepted;
import com.congthanh.orderservice.adapter.in.web.OrderDtos.CheckoutRequest;
import com.congthanh.orderservice.adapter.in.web.OrderDtos.MoneyDto;
import com.congthanh.orderservice.adapter.in.web.OrderDtos.OrderDto;
import com.congthanh.orderservice.adapter.in.web.OrderDtos.OrderSummaryDto;
import com.congthanh.orderservice.application.port.in.CancelOrderUseCase;
import com.congthanh.orderservice.application.port.in.OrderQueries;
import com.congthanh.orderservice.application.port.in.OrderQueries.CheckoutProgress;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.Item;
import com.congthanh.orderservice.application.port.in.PlaceOrderUseCase.PlaceOrderCommand;
import com.congthanh.orderservice.application.port.in.Requester;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/ecommerce")
@Tag(name = "Orders", description = "Checkout and order history")
class OrderController {

    private final PlaceOrderUseCase placeOrder;
    private final CancelOrderUseCase cancelOrder;
    private final OrderQueries queries;

    OrderController(PlaceOrderUseCase placeOrder, CancelOrderUseCase cancelOrder, OrderQueries queries) {
        this.placeOrder = placeOrder;
        this.cancelOrder = cancelOrder;
        this.queries = queries;
    }

    @Operation(summary = "Place an order and start checkout",
            description = "Returns 202 at once; poll checkoutUrl for progress (and the payment redirect URL, if any). "
                    + "Send the same Idempotency-Key to retry safely.")
    @Parameter(name = "Idempotency-Key", in = ParameterIn.HEADER, required = true)
    @PostMapping("/checkout")
    ResponseEntity<CheckoutAccepted> checkout(@Valid @RequestBody CheckoutRequest body,
                                              @AuthenticationPrincipal Jwt jwt, HttpServletRequest http) {
        var placed = placeOrder.place(new PlaceOrderCommand(
                jwt.getSubject(),
                body.items().stream().map(i -> new Item(i.productId(), i.quantity())).toList(),
                body.promotionCode(), body.paymentMethod(), body.paymentToken(), body.returnUrl(), clientIp(http),
                body.shippingAddress().toDomain(),
                body.billingAddress() == null ? null : body.billingAddress().toDomain(),
                body.note()));
        String checkoutUrl = "/api/ecommerce/orders/" + placed.orderCode() + "/checkout";
        return ResponseEntity.accepted()
                .location(URI.create("/api/ecommerce/orders/" + placed.orderCode()))
                .body(new CheckoutAccepted(placed.orderId(), placed.orderCode(), placed.status().name(),
                        MoneyDto.of(placed.total()), MoneyDto.of(placed.discount()), checkoutUrl));
    }

    @Operation(summary = "Checkout progress (saga step, payment redirect URL)")
    @GetMapping("/orders/{orderCode}/checkout")
    CheckoutProgress progress(@PathVariable String orderCode, @AuthenticationPrincipal Jwt jwt) {
        return queries.progress(orderCode, Requesters.from(jwt));
    }

    @Operation(summary = "Order detail")
    @GetMapping("/orders/{orderCode}")
    OrderDto get(@PathVariable String orderCode, @AuthenticationPrincipal Jwt jwt) {
        return OrderDto.of(queries.get(orderCode, Requesters.from(jwt)));
    }

    @Operation(summary = "My orders, newest first")
    @GetMapping("/orders")
    List<OrderSummaryDto> history(@RequestParam(defaultValue = "20") int limit, @AuthenticationPrincipal Jwt jwt) {
        return queries.history(Requesters.from(jwt), limit).stream().map(OrderSummaryDto::of).toList();
    }

    @Operation(summary = "Cancel an order whose checkout has not finished")
    @PostMapping("/orders/{orderCode}/cancel")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void cancel(@PathVariable String orderCode, @RequestBody(required = false) CancelRequest body,
                @AuthenticationPrincipal Jwt jwt) {
        cancelOrder.requestCancel(orderCode, Requesters.from(jwt), body == null ? null : body.reason());
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    static final class Requesters {
        private Requesters() {
        }

        static Requester from(Jwt jwt) {
            Object realm = jwt.getClaims().get("realm_access");
            boolean staff = false;
            if (realm instanceof java.util.Map<?, ?> m && m.get("roles") instanceof java.util.Collection<?> roles) {
                staff = roles.contains("ADMIN") || roles.contains("SUPPORT");
            }
            return new Requester(jwt.getSubject(), staff);
        }
    }
}
