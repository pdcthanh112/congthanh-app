package com.congthanh.platform.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * JWT của Keycloak → Authentication: realm role (realm_access.roles) thành ROLE_*, scope thành SCOPE_*.
 * Dùng: {@code http.oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))}.
 */
public class KeycloakJwtRoles implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>(scopes.convert(jwt));
        Object realm = jwt.getClaims().get("realm_access");
        if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> roles) {
            for (Object r : roles) {
                authorities.add(new SimpleGrantedAuthority("ROLE_" + r));
            }
        }
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
