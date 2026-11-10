package com.congthanh.orderservice.application.port.out;

import com.congthanh.orderservice.domain.model.Order;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository {

    Optional<Order> findById(String id);

    Optional<Order> findByCode(String orderCode);

    List<Order> findByCustomer(String customerId, int limit);

    /** Đơn PENDING tạo trước {@code before}: ứng viên để khởi động lại saga nếu lần start đầu lỗi. */
    List<Order> findPendingPlacedBefore(Instant before, int limit);

    /** Lưu đơn và ghi các event chưa phát vào outbox trong cùng transaction. */
    void save(Order order);
}
