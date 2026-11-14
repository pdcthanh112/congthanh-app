package com.congthanh.orderservice.application.port.in;

/** Xoá/ẩn danh dữ liệu cá nhân của khách theo yêu cầu của privacy-service (CCPA/CPRA, ADR 0027). */
public interface PersonalDataErasure {

    /** Idempotent: chạy lại cho cùng khách không đổi gì thêm. */
    void erase(String requestId, String subjectId);
}
