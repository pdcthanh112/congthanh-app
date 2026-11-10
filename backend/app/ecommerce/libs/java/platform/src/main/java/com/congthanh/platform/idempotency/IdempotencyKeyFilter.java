package com.congthanh.platform.idempotency;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.server.PathContainer;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Header {@code Idempotency-Key} cho POST/PATCH (draft-ietf-httpapi-idempotency-key-header):
 * <ul>
 *   <li>lần đầu: xử lý bình thường, lưu status + body (trừ 5xx, để client thử lại);</li>
 *   <li>gửi lại cùng key + cùng payload: trả lại đúng response cũ, kèm {@code Idempotent-Replayed: true};</li>
 *   <li>cùng key nhưng payload khác: 422; request đầu còn đang chạy: 409.</li>
 * </ul>
 * Key gắn với principal (sub của JWT) nên hai người dùng không đụng key của nhau.
 */
public class IdempotencyKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    public static final String REPLAYED = "Idempotent-Replayed";
    private static final Set<String> METHODS = Set.of("POST", "PATCH");
    private static final int MAX_KEY_LENGTH = 255;

    private final IdempotencyStore store;
    private final List<PathPattern> patterns;
    private final boolean required;

    public IdempotencyKeyFilter(IdempotencyStore store, List<String> paths, boolean required) {
        this.store = store;
        this.patterns = paths.stream().map(PathPatternParser.defaultInstance::parse).toList();
        this.required = required;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!METHODS.contains(request.getMethod())) {
            return true;
        }
        PathContainer path = PathContainer.parsePath(request.getRequestURI().substring(request.getContextPath().length()));
        return patterns.stream().noneMatch(p -> p.matches(path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            if (required) {
                problem(response, 400, "Missing Idempotency-Key", "This endpoint requires an Idempotency-Key header.");
                return;
            }
            chain.doFilter(request, response);
            return;
        }
        if (key.length() > MAX_KEY_LENGTH) {
            problem(response, 400, "Invalid Idempotency-Key", "Idempotency-Key must be at most 255 characters.");
            return;
        }

        CachedBodyRequest cached = new CachedBodyRequest(request);
        String principal = request.getUserPrincipal() != null ? request.getUserPrincipal().getName() : "anonymous";
        String hash = hash(request.getMethod(), request.getRequestURI(), cached.body);

        IdempotencyStore.Begin begin = store.begin(principal, key, hash);
        if (!begin.acquired()) {
            replay(begin.existing(), hash, response);
            return;
        }

        ContentCachingResponseWrapper wrapped = new ContentCachingResponseWrapper(response);
        boolean stored = false;
        try {
            chain.doFilter(cached, wrapped);
            int status = wrapped.getStatus();
            if (status < 500) {
                store.complete(principal, key, status, wrapped.getContentType(), wrapped.getContentAsByteArray());
                stored = true;
            }
        } finally {
            if (!stored) {
                store.release(principal, key);
            }
            wrapped.copyBodyToResponse();
        }
    }

    private void replay(IdempotencyStore.Entry entry, String hash, HttpServletResponse response) throws IOException {
        if (!entry.requestHash().equals(hash)) {
            problem(response, 422, "Idempotency-Key reused",
                    "This Idempotency-Key was already used with a different request payload.");
            return;
        }
        if (entry.state() == IdempotencyStore.State.IN_PROGRESS) {
            response.setHeader("Retry-After", "1");
            problem(response, 409, "Request in progress",
                    "A request with this Idempotency-Key is still being processed.");
            return;
        }
        response.setStatus(entry.responseCode());
        response.setHeader(REPLAYED, "true");
        if (entry.responseType() != null) {
            response.setContentType(entry.responseType());
        }
        if (entry.responseBody() != null) {
            response.getOutputStream().write(entry.responseBody());
        }
    }

    private static void problem(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}");
    }

    static String hash(String method, String uri, byte[] body) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update((method + ' ' + uri + '\n').getBytes(StandardCharsets.UTF_8));
            sha.update(body);
            return HexFormat.of().formatHex(sha.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Đọc body một lần để băm, rồi cho controller đọc lại. */
    static final class CachedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.body = request.getInputStream().readAllBytes();
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public int read(byte[] b, int off, int len) {
                    return in.read(b, off, len);
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }
    }
}
