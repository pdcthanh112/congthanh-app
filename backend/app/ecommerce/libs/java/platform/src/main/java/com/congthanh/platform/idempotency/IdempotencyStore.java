package com.congthanh.platform.idempotency;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * Lưu trạng thái Idempotency-Key trong Postgres (bảng {@code idempotency_keys}). Mỗi lệnh tự commit.
 *
 * <p>Bản ghi IN_PROGRESS giữ một lease ngắn ({@code locked_until}) kèm {@code lock_token}. Hết lease mà chưa
 * complete (pod chết giữa request) thì request gửi lại cùng payload được tiếp quản ngay, không phải chờ hết TTL.
 * complete/release chỉ có tác dụng với đúng token đang giữ, nên request cũ chạy chậm không ghi đè được người mới.
 */
public class IdempotencyStore {

    public enum State { IN_PROGRESS, COMPLETED }

    public record Entry(String requestHash, State state, Integer responseCode, String responseType,
                        byte[] responseBody, OffsetDateTime createdAt, OffsetDateTime lockedUntil) {
    }

    /** Kết quả của {@link #begin}: hoặc ta giữ key (acquired, kèm token), hoặc đã có bản ghi trước đó. */
    public record Begin(boolean acquired, Entry existing, String token) {
        static Begin acquired(String token) {
            return new Begin(true, null, token);
        }
    }

    static final Duration DEFAULT_LEASE = Duration.ofSeconds(60);

    private final JdbcClient jdbc;
    private final Duration ttl;
    private final Duration lease;
    private final Clock clock;

    public IdempotencyStore(JdbcClient jdbc, Duration ttl, Duration lease, Clock clock) {
        this.jdbc = jdbc;
        this.ttl = ttl;
        this.lease = lease;
        this.clock = clock;
    }

    public IdempotencyStore(JdbcClient jdbc, Duration ttl, Clock clock) {
        this(jdbc, ttl, DEFAULT_LEASE, clock);
    }

    public Begin begin(String principal, String key, String requestHash) {
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = UUID.randomUUID().toString();
            if (tryInsert(principal, key, requestHash, token)) {
                return Begin.acquired(token);
            }
            Optional<Entry> existing = find(principal, key);
            if (existing.isEmpty()) {
                continue; // vừa bị xoá giữa hai lệnh, thử lại
            }
            Entry e = existing.get();
            if (expired(e)) {
                jdbc.sql("DELETE FROM idempotency_keys WHERE principal = :principal AND idem_key = :key AND created_at = :created")
                        .param("principal", principal).param("key", key).param("created", e.createdAt())
                        .update();
                continue;
            }
            if (e.state() == State.IN_PROGRESS && e.requestHash().equals(requestHash) && leaseExpired(e)
                    && takeOver(principal, key, requestHash, token)) {
                return Begin.acquired(token);
            }
            return new Begin(false, e, null);
        }
        throw new IllegalStateException("could not acquire idempotency key");
    }

    /** Lưu response; trả false nếu lease đã bị request khác tiếp quản (response này bị bỏ). */
    public boolean complete(String principal, String key, String token, int code, String contentType, byte[] body) {
        return jdbc.sql("""
                        UPDATE idempotency_keys
                           SET status = 'COMPLETED', response_code = :code, response_type = :type, response_body = :body,
                               locked_until = NULL, lock_token = NULL
                         WHERE principal = :principal AND idem_key = :key AND lock_token = :token
                           AND status = 'IN_PROGRESS'""")
                .param("code", code).param("type", contentType).param("body", body)
                .param("principal", principal).param("key", key).param("token", token)
                .update() == 1;
    }

    /** Bỏ key để client thử lại (dùng khi xử lý lỗi 5xx hoặc ném exception). Chỉ xoá nếu mình còn giữ lease. */
    public void release(String principal, String key, String token) {
        jdbc.sql("""
                        DELETE FROM idempotency_keys
                         WHERE principal = :principal AND idem_key = :key AND lock_token = :token
                           AND status = 'IN_PROGRESS'""")
                .param("principal", principal).param("key", key).param("token", token)
                .update();
    }

    public int purgeExpired() {
        return jdbc.sql("DELETE FROM idempotency_keys WHERE created_at < :before")
                .param("before", now().minus(ttl))
                .update();
    }

    private boolean tryInsert(String principal, String key, String requestHash, String token) {
        OffsetDateTime now = now();
        try {
            return jdbc.sql("""
                            INSERT INTO idempotency_keys
                                   (principal, idem_key, request_hash, status, created_at, locked_until, lock_token)
                            VALUES (:principal, :key, :hash, 'IN_PROGRESS', :now, :until, :token)
                            ON CONFLICT DO NOTHING""")
                    .param("principal", principal).param("key", key).param("hash", requestHash)
                    .param("now", now).param("until", now.plus(lease)).param("token", token)
                    .update() == 1;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    /** Giành lại key IN_PROGRESS đã hết lease; UPDATE có điều kiện nên chỉ một request thắng. */
    private boolean takeOver(String principal, String key, String requestHash, String token) {
        OffsetDateTime now = now();
        return jdbc.sql("""
                        UPDATE idempotency_keys
                           SET lock_token = :token, locked_until = :until
                         WHERE principal = :principal AND idem_key = :key AND request_hash = :hash
                           AND status = 'IN_PROGRESS'
                           AND COALESCE(locked_until, created_at + make_interval(secs => :leaseSecs)) < :now""")
                .param("token", token).param("until", now.plus(lease))
                .param("principal", principal).param("key", key).param("hash", requestHash)
                .param("leaseSecs", (double) lease.toSeconds()).param("now", now)
                .update() == 1;
    }

    private Optional<Entry> find(String principal, String key) {
        return jdbc.sql("""
                        SELECT request_hash, status, response_code, response_type, response_body, created_at, locked_until
                          FROM idempotency_keys WHERE principal = :principal AND idem_key = :key""")
                .param("principal", principal).param("key", key)
                .query((rs, n) -> new Entry(
                        rs.getString("request_hash"),
                        State.valueOf(rs.getString("status")),
                        (Integer) rs.getObject("response_code"),
                        rs.getString("response_type"),
                        rs.getBytes("response_body"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("locked_until", OffsetDateTime.class)))
                .optional();
    }

    private boolean expired(Entry e) {
        return e.createdAt() != null && e.createdAt().isBefore(now().minus(ttl));
    }

    /** Bản ghi cũ (trước khi có cột locked_until) tính lease từ created_at. */
    private boolean leaseExpired(Entry e) {
        OffsetDateTime until = e.lockedUntil() != null ? e.lockedUntil()
                : e.createdAt() != null ? e.createdAt().plus(lease) : null;
        return until != null && until.isBefore(now());
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
    }
}
