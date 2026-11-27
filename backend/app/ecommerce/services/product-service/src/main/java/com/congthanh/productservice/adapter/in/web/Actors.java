package com.congthanh.productservice.adapter.in.web;

import com.congthanh.productservice.application.dto.Actor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

final class Actors {

    private Actors() {
    }

    static Actor of(Authentication auth) {
        boolean admin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);
        return new Actor(auth.getName(), admin);
    }
}
