package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

import java.util.Optional;

public interface SessionRepository {

    Optional<CheckoutSession> findById(String id);

    /** Lưu có kiểm tra version (optimistic locking); lỗi CONFLICT nếu phiên vừa bị sửa song song. */
    CheckoutSession save(CheckoutSession session);
}
