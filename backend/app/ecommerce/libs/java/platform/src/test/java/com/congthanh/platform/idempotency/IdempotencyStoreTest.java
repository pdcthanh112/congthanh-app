package com.congthanh.platform.idempotency;

import com.congthanh.platform.support.TestPostgres;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy trên Postgres thật ({@link TestPostgres}: TEST_DATABASE_URL hoặc Testcontainers). */
class IdempotencyStoreTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-12T10:00:00Z"));
    private JdbcClient jdbc;
    private IdempotencyStore store;
    private String key;

    @BeforeEach
    void setUp() {
        jdbc = JdbcClient.create(TestPostgres.dataSource());
        jdbc.sql("""
                CREATE TABLE IF NOT EXISTS idempotency_keys (
                    principal TEXT NOT NULL, idem_key TEXT NOT NULL, request_hash TEXT NOT NULL, status TEXT NOT NULL,
                    response_code INT, response_type TEXT, response_body BYTEA,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (principal, idem_key))""").update();
        jdbc.sql("ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS locked_until TIMESTAMPTZ").update();
        jdbc.sql("ALTER TABLE idempotency_keys ADD COLUMN IF NOT EXISTS lock_token TEXT").update();
        store = new IdempotencyStore(jdbc, Duration.ofHours(24), Duration.ofSeconds(30), clock);
        key = "k-" + UUID.randomUUID();
    }

    @Test
    void secondRequestSeesInProgressWhileLeaseHolds() {
        assertThat(store.begin("u", key, "h").acquired()).isTrue();

        clock.advance(Duration.ofSeconds(10));
        var second = store.begin("u", key, "h");

        assertThat(second.acquired()).isFalse();
        assertThat(second.existing().state()).isEqualTo(IdempotencyStore.State.IN_PROGRESS);
    }

    @Test
    void expiredLeaseIsTakenOverAndOldOwnerCannotOverwrite() {
        var first = store.begin("u", key, "h");

        clock.advance(Duration.ofSeconds(31));
        var retry = store.begin("u", key, "h");
        assertThat(retry.acquired()).isTrue();
        assertThat(retry.token()).isNotEqualTo(first.token());

        // Request cũ chạy chậm xong sau: không được ghi đè, cũng không được xoá key của người mới.
        assertThat(store.complete("u", key, first.token(), 201, "application/json", "{\"old\":1}".getBytes())).isFalse();
        store.release("u", key, first.token());

        assertThat(store.complete("u", key, retry.token(), 201, "application/json", "{\"new\":1}".getBytes())).isTrue();
        var replay = store.begin("u", key, "h");
        assertThat(replay.acquired()).isFalse();
        assertThat(new String(replay.existing().responseBody())).isEqualTo("{\"new\":1}");
    }

    @Test
    void differentPayloadIsNotTakenOverEvenAfterLease() {
        store.begin("u", key, "h");
        clock.advance(Duration.ofMinutes(5));

        var other = store.begin("u", key, "other");

        assertThat(other.acquired()).isFalse();
        assertThat(other.existing().requestHash()).isEqualTo("h");
    }

    @Test
    void legacyRowWithoutLeaseUsesCreatedAt() {
        jdbc.sql("INSERT INTO idempotency_keys (principal, idem_key, request_hash, status, created_at) VALUES ('u', :k, 'h', 'IN_PROGRESS', :t)")
                .param("k", key).param("t", clock.instant().atOffset(ZoneOffset.UTC)).update();
        clock.advance(Duration.ofSeconds(31));

        assertThat(store.begin("u", key, "h").acquired()).isTrue();
    }

    static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration d) {
            now = now.plus(d);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
