package com.congthanh.productservice.config;

import com.congthanh.platform.security.KeycloakJwtRoles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/** Thay JWT tự ký (SecurityFilter cũ) bằng resource server Keycloak. Đọc công khai, ghi cần SELLER/ADMIN. */
@Configuration
class SecurityConfig {

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health/**", "/actuator/prometheus", "/actuator/info").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/graphiql/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/ecommerce/**").permitAll()
                        .requestMatchers("/graphql").permitAll()
                        .requestMatchers("/api/ecommerce/**").hasAnyRole("SELLER", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(new KeycloakJwtRoles())))
                .build();
    }
}
