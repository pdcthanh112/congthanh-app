package com.congthanh.orderservice.domain.model;

/**
 * PENDING → RESERVED → CONFIRMED, hoặc PENDING/RESERVED → CANCELLED (saga bù trừ).
 * CONFIRMED là điểm không quay lại: huỷ sau đó là luồng trả hàng/hoàn tiền, không phải saga checkout.
 */
public enum OrderStatus {
    PENDING, RESERVED, CONFIRMED, CANCELLED
}
