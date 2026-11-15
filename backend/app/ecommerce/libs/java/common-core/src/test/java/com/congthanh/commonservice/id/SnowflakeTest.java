package com.congthanh.commonservice.id;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeTest {

    @Test
    void encodesTimeNodeAndSequence() {
        Instant t = Instant.parse("2026-11-15T00:00:00Z");
        Snowflake gen = new Snowflake(37, Clock.fixed(t, ZoneOffset.UTC));
        long a = gen.next();
        long b = gen.next();
        assertThat(a >>> 22).isEqualTo(t.toEpochMilli() - Snowflake.EPOCH_MS);
        assertThat((a >>> 12) & 0x3FF).isEqualTo(37);
        assertThat(a & 0xFFF).isZero();
        assertThat(b).isEqualTo(a + 1);
    }

    @Test
    void uniqueAndIncreasing() {
        Set<Long> seen = new HashSet<>();
        long prev = -1;
        for (int i = 0; i < 20_000; i++) {
            long id = Snowflake.nextId();
            assertThat(id).isGreaterThan(prev);
            assertThat(seen.add(id)).isTrue();
            prev = id;
        }
        assertThat(Snowflake.nextString()).matches("\\d+");
    }

    @Test
    void rejectsOutOfRangeNode() {
        assertThatThrownBy(() -> new Snowflake(1024, Clock.systemUTC())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void erasedIdIsStableAndOpaque() {
        assertThat(ErasedIds.of("c-1")).isEqualTo(ErasedIds.of("c-1")).startsWith("erased-").hasSize(23);
        assertThat(ErasedIds.of("c-2")).isNotEqualTo(ErasedIds.of("c-1"));
    }
}
