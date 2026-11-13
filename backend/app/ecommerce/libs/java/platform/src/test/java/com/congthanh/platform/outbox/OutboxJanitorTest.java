package com.congthanh.platform.outbox;

import com.congthanh.platform.support.TestPostgres;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.time.Clock;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/** Chạy trên Postgres thật ({@link TestPostgres}: TEST_DATABASE_URL hoặc Testcontainers). */
class OutboxJanitorTest {

    private SingleConnectionDataSource ds;
    private JdbcClient jdbc;
    private final String schema = "janitor_java_" + System.nanoTime();

    @BeforeEach
    void setUp() {
        ds = TestPostgres.dataSource();
        jdbc = JdbcClient.create(ds);
        jdbc.sql("CREATE SCHEMA " + schema).update();
        jdbc.sql("SET search_path = " + schema).update();
        jdbc.sql("""
                CREATE TABLE outbox (id UUID PRIMARY KEY, aggregatetype TEXT NOT NULL, aggregateid TEXT NOT NULL,
                    type TEXT NOT NULL, payload BYTEA NOT NULL, traceparent TEXT,
                    created_at TIMESTAMPTZ NOT NULL DEFAULT now())""").update();
        jdbc.sql("""
                CREATE TABLE processed_events (event_id TEXT NOT NULL, consumer TEXT NOT NULL,
                    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (event_id, consumer))""").update();
    }

    @AfterEach
    void tearDown() {
        if (ds != null) {
            jdbc.sql("DROP SCHEMA " + schema + " CASCADE").update();
            ds.destroy();
        }
    }

    @Test
    void purgesOnlyRowsOlderThanRetention() {
        jdbc.sql("""
                INSERT INTO outbox (id, aggregatetype, aggregateid, type, payload, created_at) VALUES
                  (gen_random_uuid(), 'order', '1', 't', '\\x00', now() - interval '2 hours'),
                  (gen_random_uuid(), 'order', '2', 't', '\\x00', now() - interval '5 minutes')""").update();
        jdbc.sql("""
                INSERT INTO processed_events (event_id, consumer, processed_at) VALUES
                  ('old', 'g', now() - interval '20 days'), ('new', 'g', now())""").update();

        var purged = new OutboxJanitor(jdbc, Duration.ofHours(1), Duration.ofDays(14), Clock.systemUTC()).purge();

        assertThat(purged).isEqualTo(new OutboxJanitor.Purged(1, 1));
        assertThat(jdbc.sql("SELECT count(*) FROM outbox").query(Long.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT event_id FROM processed_events").query(String.class).single()).isEqualTo("new");
    }
}
