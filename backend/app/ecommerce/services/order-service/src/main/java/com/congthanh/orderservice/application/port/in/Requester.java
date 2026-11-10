package com.congthanh.orderservice.application.port.in;

/** Người gọi đã xác thực (sub của JWT) và có phải nhân viên backoffice không. */
public record Requester(String subject, boolean staff) {

    public boolean canAccess(String ownerId) {
        return staff || subject.equals(ownerId);
    }
}
