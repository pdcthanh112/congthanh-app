package com.congthanh.commonservice.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RequestLoggingFilterTest {

    private final RequestLoggingFilter filter = new RequestLoggingFilter(List.of("/actuator/**"));

    @Test
    void skipsExcludedPaths() {
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/actuator/health/liveness"))).isTrue();
        assertThat(filter.shouldNotFilter(new MockHttpServletRequest("GET", "/api/ecommerce/orders"))).isFalse();
    }

    @Test
    void passesTheRequestDownTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/ecommerce/orders");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }
}
