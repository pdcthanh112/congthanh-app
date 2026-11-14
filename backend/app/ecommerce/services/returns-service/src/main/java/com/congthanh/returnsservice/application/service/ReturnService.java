package com.congthanh.returnsservice.application.service;

import com.congthanh.returnsservice.application.port.in.CustomerReturns;
import com.congthanh.returnsservice.application.port.in.OrderProjection;
import com.congthanh.returnsservice.application.port.in.RefundOutcomes;
import com.congthanh.returnsservice.application.port.in.ReturnAdmin;
import com.congthanh.returnsservice.application.port.out.Ids;
import com.congthanh.returnsservice.application.port.out.OrderSnapshots;
import com.congthanh.returnsservice.application.port.out.RefundWorkflows;
import com.congthanh.returnsservice.application.port.out.ReturnRepository;
import com.congthanh.returnsservice.domain.exception.ReturnException;
import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.congthanh.returnsservice.domain.model.OrderSnapshot;
import com.congthanh.returnsservice.domain.model.RefundMethod;
import com.congthanh.returnsservice.domain.model.ReturnLine;
import com.congthanh.returnsservice.domain.model.ReturnPolicy;
import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
@RequiredArgsConstructor
public class ReturnService implements CustomerReturns, ReturnAdmin, RefundOutcomes, OrderProjection {

    private static final Logger log = LoggerFactory.getLogger(ReturnService.class);
    private static final Duration STUCK_AFTER = Duration.ofMinutes(5);

    private final ReturnRepository returns;
    private final OrderSnapshots orders;
    private final RefundWorkflows workflows;
    private final Ids ids;
    private final ReturnPolicy policy;
    private final TransactionTemplate tx;
    private final Clock clock;

    @Override
    public ReturnRequest open(String customerId, String orderId, List<ReturnLine> lines, RefundMethod method) {
        return tx.execute(s -> {
            OrderSnapshot order = orders.lock(orderId)
                    .orElseThrow(() -> ReturnException.notFound("order " + orderId));
            ReturnRequest r = ReturnRequest.open(ids.next(), ids.rmaCode(), order, customerId, lines, method, policy,
                    returns.openQuantities(orderId), clock.instant());
            returns.save(r);
            return r;
        });
    }

    @Override
    public List<ReturnRequest> mine(String customerId) {
        return returns.byCustomer(customerId, 100);
    }

    @Override
    public ReturnRequest get(String returnId, String callerId, boolean admin) {
        ReturnRequest r = load(returnId);
        if (!admin && !r.customerId().equals(callerId)) {
            // Không lộ việc yêu cầu có tồn tại với người khác.
            throw ReturnException.notFound("return " + returnId);
        }
        return r;
    }

    @Override
    public void cancel(String returnId, String customerId) {
        mutate(returnId, r -> r.cancel(customerId, clock.instant()));
    }

    @Override
    public ReturnRequest approve(String returnId, String by) {
        return mutate(returnId, r -> r.approve(by, clock.instant()));
    }

    @Override
    public ReturnRequest reject(String returnId, String by, String reason) {
        return mutate(returnId, r -> r.reject(reason, clock.instant()));
    }

    @Override
    public ReturnRequest receive(String returnId, Map<String, ItemCondition> conditions, String by) {
        ReturnRequest r = tx.execute(s -> {
            ReturnRequest loaded = load(returnId);
            OrderSnapshot order = orders.lock(loaded.orderId())
                    .orElseThrow(() -> ReturnException.notFound("order " + loaded.orderId()));
            BigDecimal left = order.total().subtract(returns.refundCommitted(loaded.orderId(), returnId));
            loaded.receive(conditions, policy, left, clock.instant());
            returns.save(loaded);
            return loaded;
        });
        if (r.status() == ReturnStatus.REFUND_PENDING) {
            startRefund(r);
        }
        return r;
    }

    @Override
    public ReturnRequest retryRefund(String returnId, String by) {
        ReturnRequest r = mutate(returnId, x -> x.retryRefund(clock.instant()));
        startRefund(r);
        return r;
    }

    @Override
    public List<ReturnRequest> byStatus(ReturnStatus status, int limit) {
        return returns.byStatus(status, Math.clamp(limit, 1, 500));
    }

    @Override
    public int resumeStuckRefunds() {
        List<ReturnRequest> stuck = returns.stuck(ReturnStatus.REFUND_PENDING, clock.instant().minus(STUCK_AFTER), 100);
        stuck.forEach(this::startRefund);
        return stuck.size();
    }

    @Override
    public void refunded(String returnId, String paymentId) {
        mutate(returnId, r -> r.refunded(paymentId, clock.instant()));
    }

    @Override
    public void refundFailed(String returnId, String reason) {
        mutate(returnId, r -> r.refundFailed(reason, clock.instant()));
    }

    @Override
    public void placed(OrderSnapshot order) {
        orders.upsertPlaced(order);
    }

    @Override
    public void confirmed(String orderId, String paymentId) {
        orders.markStatus(orderId, "CONFIRMED", paymentId);
    }

    @Override
    public void cancelled(String orderId) {
        orders.markStatus(orderId, "CANCELLED", null);
    }

    @Override
    public void delivered(String orderId, Instant at) {
        orders.markDelivered(orderId, at);
    }

    private void startRefund(ReturnRequest r) {
        try {
            workflows.start(new RefundWorkflows.RefundRequest(r.id(), r.orderId(), r.customerId(), r.refundAmount(),
                    r.currency(), "return " + r.rmaCode(), r.refundMethod().name()));
        } catch (RuntimeException e) {
            // Đã commit REFUND_PENDING; resumeStuckRefunds sẽ thử lại.
            log.warn("could not start refund workflow for return {}: {}", r.id(), e.toString());
        }
    }

    private ReturnRequest mutate(String returnId, Consumer<ReturnRequest> change) {
        return tx.execute(s -> {
            ReturnRequest r = load(returnId);
            change.accept(r);
            returns.save(r);
            return r;
        });
    }

    private ReturnRequest load(String id) {
        return returns.findById(id).orElseThrow(() -> ReturnException.notFound("return " + id));
    }
}
