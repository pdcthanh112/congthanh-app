package com.congthanh.platform.support;

import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Postgres cho test: TEST_DATABASE_URL (postgres://user:pass@host:port/db) nếu có, không thì một container
 * Testcontainers dùng chung cho cả JVM test. Không có Docker: local thì skip, trên CI thì fail.
 */
public final class TestPostgres {

    private static final String IMAGE = "pgvector/pgvector:0.8.1-pg18-trixie";
    private static volatile String[] jdbc;
    private static volatile RuntimeException failure;

    private TestPostgres() {
    }

    /** DataSource một kết nối, đóng bằng {@code destroy()}. */
    public static SingleConnectionDataSource dataSource() {
        String[] c = connection();
        return new SingleConnectionDataSource(c[0], c[1], c[2], true);
    }

    private static synchronized String[] connection() {
        if (jdbc == null && failure == null) {
            String url = System.getenv("TEST_DATABASE_URL");
            if (url != null && !url.isBlank()) {
                URI u = URI.create(url);
                String[] cred = u.getUserInfo() == null ? new String[]{"postgres", ""} : u.getUserInfo().split(":", 2);
                jdbc = new String[]{"jdbc:postgresql://" + u.getHost() + ":" + u.getPort() + u.getPath(),
                        cred[0], cred.length > 1 ? cred[1] : ""};
            } else {
                try {
                    var pg = new PostgreSQLContainer(IMAGE);
                    pg.start(); // Ryuk dọn container khi JVM test kết thúc
                    jdbc = new String[]{pg.getJdbcUrl(), pg.getUsername(), pg.getPassword()};
                } catch (RuntimeException e) {
                    failure = e;
                }
            }
        }
        if (failure != null) {
            if (System.getenv("CI") != null) {
                fail("TEST_DATABASE_URL not set and Testcontainers failed", failure);
            }
            assumeTrue(false, "TEST_DATABASE_URL not set and no test container could be started: " + failure);
        }
        return jdbc;
    }
}
