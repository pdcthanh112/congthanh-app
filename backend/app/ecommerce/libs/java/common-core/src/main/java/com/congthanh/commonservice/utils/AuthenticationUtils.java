package com.congthanh.commonservice.utils;

import com.congthanh.commonservice.exception.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public final class AuthenticationUtils {

    private static final String ACCESS_DENIED = "ACCESS_DENIED";

    private AuthenticationUtils() {
    }

    public static String extractUserId() {
        Authentication authentication = getAuthentication();

        if (authentication instanceof AnonymousAuthenticationToken) {
            throw new AccessDeniedException(ACCESS_DENIED);
        }

        JwtAuthenticationToken contextHolder = (JwtAuthenticationToken) authentication;

        return contextHolder.getToken().getSubject();
    }

    public static String extractJwt() {
        return ((Jwt) getAuthentication().getPrincipal()).getTokenValue();
    }

    public static Authentication getAuthentication() {
        return SecurityContextHolder.getContext().getAuthentication();
    }
}
