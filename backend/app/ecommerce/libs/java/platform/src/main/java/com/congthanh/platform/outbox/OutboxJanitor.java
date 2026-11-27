package com.congthanh.platform.outbox;

import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Dọn {@code outbox} và {@code processed_events} quá hạn, và bảo trì partition theo tháng của service (nếu có).
 *
 * <p>Debezium đọc event từ WAL (replication slot giữ WAL tới khi connector xác nhận), không đọc lại hàng trong
 * bảng, nên xoá hàng outbox đã ghi không làm mất event; chỉ giữ một lúc để tra cứu. {@code processed_events} phải
 * giữ lâu hơn retention của topic, nếu không consumer đọc lại từ đầu sẽ xử lý trùng.
 */
public class OutboxJanitor {

    private static final int BATCH = 5000;

    private final JdbcClient jdbc;
    private final Duration outboxRetention;
    private final Duration processedRetention;
    private final Clock clock;

    public OutboxJanitor(JdbcClient jdbc, Duration outboxRetention, Duration processedRetention, Clock clock) {
        this.jdbc = jdbc;
        this.outboxRetention = outboxRetention;
        this.processedRetention = processedRetention;
        this.clock = clock;
    }

    public record Purged(long outbox, long processedEvents) {
    }

    public Purged purge() {
        OffsetDateTime now = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        return new Purged(purge("outbox", "created_at", now.minus(outboxRetention)),
                purge("processed_events", "processed_at", now.minus(processedRetention)));
    }

    /**
     * Gọi {@code maintain_partitions()} mà migration của service tạo khi service có bảng partition theo tháng, để luôn
     * có sẵn partition các tháng tới. Trả số partition vừa tạo; schema không có hàm này thì trả 0.
     */
    public int maintainPartitions() {
        Boolean exists = jdbc.sql("SELECT to_regprocedure('maintain_partitions()') IS NOT NULL")
                .query(Boolean.class).single();
        if (!Boolean.TRUE.equals(exists)) {
            return 0;
        }
        return jdbc.sql("SELECT maintain_partitions()").query(Integer.class).single();
    }

    private long purge(String table, String column, OffsetDateTime before) {
        Boolean exists = jdbc.sql("SELECT to_regclass(:t) IS NOT NULL").param("t", table).query(Boolean.class).single();
        if (!Boolean.TRUE.equals(exists)) {
            return 0;
        }
        // Xoá theo lô để không giữ khoá lâu và không tạo một transaction khổng lồ.
        String sql = "DELETE FROM " + table + " WHERE ctid = ANY(ARRAY(SELECT ctid FROM " + table
                + " WHERE " + column + " < :before LIMIT :batch))";
        long total = 0;
        int n;
        do {
            n = jdbc.sql(sql).param("before", before).param("batch", BATCH).update();
            total += n;
        } while (n == BATCH);
        return total;
    }
}
