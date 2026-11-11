package com.congthanh.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class StreamConfigTest {
    @Test
    void defaultsToAllPipelinesAndPlaintext() {
        StreamConfig c = StreamConfig.fromEnv(Map.of());
        assertThat(c.pipelines()).containsExactlyInAnyOrderElementsOf(StreamConfig.ALL);
        assertThat(c.kafkaProperties()).doesNotContainKey("sasl.jaas.config");
    }

    @Test
    void scramWhenSasl() {
        StreamConfig c = StreamConfig.fromEnv(Map.of("KAFKA_SECURITY_PROTOCOL", "SASL_SSL", "KAFKA_USERNAME", "u", "KAFKA_PASSWORD", "p",
                "PIPELINES", "stock-cover, customer-risk"));
        assertThat(c.pipelines()).containsExactly("stock-cover", "customer-risk");
        assertThat(c.kafkaProperties().getProperty("sasl.jaas.config")).contains("username=\"u\"");
        assertThat(c.toString()).doesNotContain("p\"");
    }

    @Test
    void rejectsUnknownPipelines() {
        assertThatThrownBy(() -> StreamConfig.fromEnv(Map.of("PIPELINES", "nope"))).isInstanceOf(IllegalArgumentException.class);
    }
}
