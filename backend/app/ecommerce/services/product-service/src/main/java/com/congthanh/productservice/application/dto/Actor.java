package com.congthanh.productservice.application.dto;

/**
 * Người gọi API ghi: {@code subject} là sub trong JWT Keycloak, {@code admin} khi có role ADMIN.
 */
public record Actor(String subject, boolean admin) {

    /** Owner gán cho sản phẩm người này tạo: SELLER sở hữu sản phẩm mình tạo, ADMIN tạo sản phẩm của sàn. */
    public String ownerOfNewProduct() {
        return admin ? null : subject;
    }
}
