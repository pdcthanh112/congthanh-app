package com.congthanh.commonservice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

/**
 * Access log một dòng cho mỗi request: method, path, status, thời gian, người gọi. Không ghi header,
 * query string hay body, vì đó là nơi token và dữ liệu cá nhân hay nằm.
 */
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger("http.access");

    private final List<PathPattern> excluded;

    public RequestLoggingFilter(List<String> excludePaths) {
        this.excluded = excludePaths.stream().map(PathPatternParser.defaultInstance::parse).toList();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        PathContainer path = PathContainer.parsePath(request.getRequestURI());
        return excluded.stream().anyMatch(p -> p.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            int status = failed && response.getStatus() < 400 ? 500 : response.getStatus();
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            Principal principal = request.getUserPrincipal();
            log.atLevel(status >= 500 ? Level.WARN : Level.INFO)
                    .addKeyValue("http.method", request.getMethod())
                    .addKeyValue("http.path", request.getRequestURI())
                    .addKeyValue("http.status", status)
                    .addKeyValue("duration_ms", durationMs)
                    .addKeyValue("user", principal != null ? principal.getName() : "anonymous")
                    .log("{} {} {} {}ms", request.getMethod(), request.getRequestURI(), status, durationMs);
        }
    }
}
