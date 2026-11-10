package com.congthanh.productservice.domain.model.enums;

public enum Role {

    ADMIN("admin"),
    USER("user");

    private final String role;

    Role(String role) {
        this.role = role;
    }

    public String getValue() {
        return role;
    }

}
