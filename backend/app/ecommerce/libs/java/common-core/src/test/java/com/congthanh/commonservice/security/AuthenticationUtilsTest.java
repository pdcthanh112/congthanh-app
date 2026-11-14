package com.congthanh.commonservice.security;

import com.congthanh.commonservice.exception.UnauthenticatedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationUtilsTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void noAuthenticationMeansNoUser() {
        assertThat(AuthenticationUtils.currentUserId()).isEmpty();
        assertThatThrownBy(AuthenticationUtils::requireUserId).isInstanceOf(UnauthenticatedException.class);
    }

    @Test
    void anonymousIsNotAUser() {
        SecurityContextHolder.getContext().setAuthentication(new AnonymousAuthenticationToken(
                "key", "anonymousUser", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")));

        assertThat(AuthenticationUtils.currentUserId()).isEmpty();
    }

    @Test
    void jwtSubjectIsTheUserId() {
        Jwt jwt = Jwt.withTokenValue("token-abc").header("alg", "none").subject("user-1")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        assertThat(AuthenticationUtils.requireUserId()).isEqualTo("user-1");
        assertThat(AuthenticationUtils.currentJwt()).contains("token-abc");
    }
}
