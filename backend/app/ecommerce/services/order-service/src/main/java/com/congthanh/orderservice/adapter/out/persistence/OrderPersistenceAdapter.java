package com.congthanh.orderservice.adapter.out.persistence;

import com.congthanh.orderservice.application.port.out.OrderRepository;
import com.congthanh.orderservice.domain.event.OrderEvent;
import com.congthanh.orderservice.domain.model.CheckoutDetails;
import com.congthanh.orderservice.domain.model.Money;
import com.congthanh.orderservice.domain.model.Order;
import com.congthanh.orderservice.domain.model.OrderLine;
import com.congthanh.orderservice.domain.model.OrderStatus;
import com.congthanh.orderservice.domain.model.PaymentMethod;
import com.congthanh.platform.outbox.OutboxWriter;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
class OrderPersistenceAdapter implements OrderRepository {

    static final String AGGREGATE = "order";

    private final OrderJpaRepository jpa;
    private final OutboxWriter outbox;

    OrderPersistenceAdapter(OrderJpaRepository jpa, OutboxWriter outbox) {
        this.jpa = jpa;
        this.outbox = outbox;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findById(String id) {
        return jpa.findWithLinesById(id).map(OrderPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Order> findByCode(String orderCode) {
        return jpa.findByOrderCode(orderCode).map(OrderPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findByCustomer(String customerId, int limit) {
        return jpa.findByCustomerIdOrderByPlacedAtDesc(customerId, Limit.of(limit)).stream()
                .map(OrderPersistenceAdapter::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Order> findPendingPlacedBefore(Instant before, int limit) {
        return jpa.findByStatusAndPlacedAtBeforeOrderByPlacedAt(OrderStatus.PENDING.name(), before, Limit.of(limit))
                .stream().map(OrderPersistenceAdapter::toDomain).toList();
    }

    /** Lưu đơn + ghi outbox trong cùng transaction (MANDATORY ở OutboxWriter bảo đảm điều đó). */
    @Override
    @Transactional
    public void save(Order order) {
        OrderEntity e = jpa.findById(order.id()).orElseGet(OrderEntity::new);
        if (e.id != null && e.version != order.version()) {
            throw new org.springframework.orm.ObjectOptimisticLockingFailureException(OrderEntity.class, order.id());
        }
        apply(order, e);
        jpa.save(e);
        for (OrderEvent event : order.pullEvents()) {
            outbox.write(AGGREGATE, order.id(), OrderEventMapper.toProto(event));
        }
    }

    private static void apply(Order o, OrderEntity e) {
        boolean isNew = e.id == null;
        e.id = o.id();
        e.orderCode = o.orderCode();
        e.customerId = o.customerId();
        e.status = o.status().name();
        e.currency = o.total().currency();
        e.subtotal = o.subtotal().amount();
        e.discount = o.discount().amount();
        e.shippingFee = o.shippingFee().amount();
        e.tax = o.tax().amount();
        e.total = o.total().amount();
        e.promotionCode = o.promotionCode();
        e.paymentMethod = o.checkout().paymentMethod().name();
        e.paymentToken = o.checkout().paymentToken();
        e.returnUrl = o.checkout().returnUrl();
        e.clientIp = o.checkout().clientIp();
        e.reservationId = o.reservationId();
        e.paymentId = o.paymentId();
        e.cancelReason = o.cancelReason();
        e.note = o.note();
        e.shipping = AddressEmbeddable.from(o.shippingAddress());
        e.billing = AddressEmbeddable.from(o.billingAddress());
        e.placedAt = o.placedAt();
        e.updatedAt = o.updatedAt();
        if (isNew) {
            // Dòng hàng bất biến sau khi đặt.
            for (OrderLine l : o.lines()) {
                OrderLineEntity le = new OrderLineEntity();
                le.order = e;
                le.productId = l.productId();
                le.sku = l.sku();
                le.name = l.name();
                le.quantity = l.quantity();
                le.unitPrice = l.unitPrice().amount();
                e.lines.add(le);
            }
        }
    }

    static Order toDomain(OrderEntity e) {
        return Order.rehydrate()
                .id(e.id).orderCode(e.orderCode).customerId(e.customerId)
                .status(OrderStatus.valueOf(e.status))
                .lines(e.lines.stream().map(l -> new OrderLine(l.productId, l.sku, l.name, l.quantity,
                        new Money(l.unitPrice, e.currency))).toList())
                .subtotal(new Money(e.subtotal, e.currency))
                .discount(new Money(e.discount, e.currency))
                .shippingFee(new Money(e.shippingFee, e.currency))
                .tax(new Money(e.tax, e.currency))
                .total(new Money(e.total, e.currency))
                .promotionCode(e.promotionCode)
                .checkout(new CheckoutDetails(PaymentMethod.valueOf(e.paymentMethod), e.paymentToken, e.returnUrl, e.clientIp))
                .shippingAddress(e.shipping == null ? null : e.shipping.toDomain())
                .billingAddress(e.billing == null ? null : e.billing.toDomain())
                .note(e.note).reservationId(e.reservationId).paymentId(e.paymentId).cancelReason(e.cancelReason)
                .placedAt(e.placedAt).updatedAt(e.updatedAt).version(e.version)
                .build();
    }
}
