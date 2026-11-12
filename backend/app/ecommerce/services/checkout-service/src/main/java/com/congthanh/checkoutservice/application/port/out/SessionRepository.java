package com.congthanh.checkoutservice.application.port.out;

import com.congthanh.checkoutservice.domain.model.CheckoutSession;

import java.util.Optional;

public interface SessionRepository {

    Optional<CheckoutSession> findById(String id);

    CheckoutSession save(CheckoutSession session);
}
