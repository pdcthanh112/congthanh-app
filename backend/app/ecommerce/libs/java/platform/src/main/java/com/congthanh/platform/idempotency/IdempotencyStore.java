package com.congthanh.platform.idempotency;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

/** Lưu trạng thái Idempotency-Key trong Postgres (bảng {@code idempotency_keys}). Mỗi lệnh tự commit. */
public class IdempotencyStore {

    public enum State { IN_PROGRESS, COMPLETED }

    public record Entry(String requestHash, State state, Integer responseCode, String responseType,
                        byte[] responseBody, OffsetDateTime createdAt) {
    }

    /** Kết quả của {@link #begin}: hoặc ta giữ key (acquired), hoặc đã có bản ghi trước đó. */
    public record Begin(boolean acquired, Entry existing) {
    }

    private final JdbcClient jdbc;
    private final Duration ttl;
    private final Clock clock;

    public IdempotencyStore(JdbcClient jdbc, Duration ttl, Clock clock) {
        this.jdbc = jdbc;
        this.ttl = ttl;
        this.clock = clock;
    }

    public Begin begin(String principal, String key, String requestHash) {
        for (int attempt = 0; attempt < 2; attempt++) {
            if (tryInsert(principal, key, requestHash)) {
                return new Begin(true, null);
            }
            Optional<Entry> existing = find(principal, key);
            if (existing.isEmpty()) {
                continue; // vừa bị xoá giữa hai lệnh, thử lại
            }
            if (expired(existing.get())) {
                release(principal, key);
                continue;
            }
            return new Begin(false, existing.get());
        }
        throw new IllegalStateException("could not acquire idempotency key");
    }

    public void complete(String principal, String key, int code, String contentType, byte[] body) {
        jdbc.sql("""
                        UPDATE idempotency_keys
                           SET status = 'COMPLETED', response_code = :code, response_type = :type, response_body = :body
                         WHERE principal = :principal AND idem_key = :key""")
                .param("code", code).param("type", contentType).param("body", body)
                .param("principal", principal).param("key", key)
                .update();
    }

    /** Bỏ key để client thử lại (dùng khi xử lý lỗi 5xx hoặc ném exception). */
    public void release(String principal, String key) {
        jdbc.sql("DELETE FROM idempotency_keys WHERE principal = :principal AND idem_key = :key")
                .param("principal", principal).param("key", key)
                .update();
    }

    public int purgeExpired() {
        return jdbc.sql("DELETE FROM idempotency_keys WHERE created_at < :before")
                .param("before", OffsetDateTime.now(clock).minus(ttl))
                .update();
    }

    private boolean tryInsert(String principal, String key, String requestHash) {
        try {
            return jdbc.sql("""
                            INSERT INTO idempotency_keys (principal, idem_key, request_hash, status, created_at)
                            VALUES (:principal, :key, :hash, 'IN_PROGRESS', :now)
                            ON CONFLICT DO NOTHING""")
                    .param("principal", principal).param("key", key).param("hash", requestHash)
                    .param("now", OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC))
                    .update() == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    private Optional<Entry> find(String principal, String key) {
        return jdbc.sql("""
                        SELECT request_hash, status, response_code, response_type, response_body, created_at
                          FROM idempotency_keys WHERE principal = :principal AND idem_key = :key""")
                .param("principal", principal).param("key", key)
                .query((rs, n) -> new Entry(
                        rs.getString("request_hash"),
                        State.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("response_code"),
                        rs.getString("response_type"),
                        rs.getBytes("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class)))
                .optional();
    }

    private boolean expired(Entry e) {
        return e.createdAt() != null && e.createdAt().isBefore(OffsetDateTime.now(clock).minus(ttl));
    }
}
