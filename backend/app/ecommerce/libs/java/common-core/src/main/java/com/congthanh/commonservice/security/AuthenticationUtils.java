package com.congthanh.commonservice.security;

import com.congthanh.commonservice.exception.UnauthenticatedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Optional;

public final class AuthenticationUtils {

    private AuthenticationUtils() {
    }

    public static Optional<Authentication> currentAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.of(authentication);
    }

    public static Optional<String> currentUserId() {
        return currentAuthentication().map(Authentication::getName);
    }

    public static String requireUserId() {
        return currentUserId().orElseThrow(() -> new UnauthenticatedException("authentication is required"));
    }

    public static Optional<String> currentJwt() {
        return currentAuthentication()
                .filter(JwtAuthenticationToken.class::isInstance)
                .map(a -> ((JwtAuthenticationToken) a).getToken().getTokenValue());
    }
}
