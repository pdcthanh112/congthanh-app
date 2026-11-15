package com.congthanh.commonservice.id;

import java.time.Clock;

public final class Snowflake {

    public static final long EPOCH_MS = 1609459200000L; // 2021-01-01T00:00:00Z
    static final int NODE_BITS = 10;
    static final int SEQUENCE_BITS = 12;
    public static final long MAX_NODE = (1L << NODE_BITS) - 1;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1;

    private static final Snowflake DEFAULT = new Snowflake(nodeFromEnv(), Clock.systemUTC());

    private final long node;
    private final Clock clock;
    private long lastMs = -1L;
    private long sequence;

    public Snowflake(long node, Clock clock) {
        if (node < 0 || node > MAX_NODE) {
            throw new IllegalArgumentException("NODE_ID phải trong 0.." + MAX_NODE + ", nhận " + node);
        }
        this.node = node;
        this.clock = clock;
    }

    public static long nextId() {
        return DEFAULT.next();
    }

    public static String nextString() {
        return Long.toString(DEFAULT.next());
    }

    public synchronized long next() {
        long now = clock.millis();
        if (now < lastMs) {
            now = lastMs;
        }
        if (now == lastMs) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                while (now <= lastMs) {
                    now = Math.max(clock.millis(), now);
                    if (now <= lastMs) {
                        Thread.onSpinWait();
                    }
                }
            }
        } else {
            sequence = 0;
        }
        lastMs = now;
        return ((now - EPOCH_MS) << (NODE_BITS + SEQUENCE_BITS)) | (node << SEQUENCE_BITS) | sequence;
    }

    public String nextAsString() {
        return Long.toString(next());
    }

    static long nodeFromEnv() {
        String raw = System.getenv("NODE_ID");
        if (raw != null && !raw.isBlank()) {
            long node = Long.parseLong(raw.trim());
            if (node < 0 || node > MAX_NODE) {
                throw new IllegalArgumentException("NODE_ID phải trong 0.." + MAX_NODE + ", nhận " + node);
            }
            return node;
        }
        String host = System.getenv("HOSTNAME");
        if (host == null || host.isBlank()) {
            host = String.valueOf(ProcessHandle.current().pid());
        }
        return Math.floorMod(host.hashCode(), (int) MAX_NODE + 1);
    }
}
