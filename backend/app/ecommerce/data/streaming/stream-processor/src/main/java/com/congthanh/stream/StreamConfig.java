package com.congthanh.stream;

import java.io.Serializable;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

public record StreamConfig(
        String brokers,
        String securityProtocol,
        String saslMechanism,
        String username,
        String password,
        String groupPrefix,
        Set<String> pipelines,
        double stockCoverHours,
        long checkpointMillis) implements Serializable {

    public static final String RISK = "customer-risk";
    public static final String TRENDING = "product-trending";
    public static final String STOCK = "stock-cover";
    public static final Set<String> ALL = Set.of(RISK, TRENDING, STOCK);

    public static final String TOPIC_ORDER = "ecommerce.order.events";
    public static final String TOPIC_PAYMENT = "ecommerce.payment.events";
    public static final String TOPIC_CART = "ecommerce.cart.events";
    public static final String TOPIC_CUSTOMER = "ecommerce.customer.events";
    public static final String TOPIC_INVENTORY = "ecommerce.inventory.events";
    public static final String OUT_RISK = "ecommerce.stream.customer-risk";
    public static final String OUT_TRENDING = "ecommerce.stream.product-trending";
    public static final String OUT_STOCK = "ecommerce.stream.stock-cover";

    public static StreamConfig fromEnv(Map<String, String> env) {
        String list = env.getOrDefault("PIPELINES", "all").trim();
        Set<String> pipes = list.equals("all") || list.isEmpty()
                ? new LinkedHashSet<>(ALL)
                : Arrays.stream(list.split(",")).map(String::trim).filter(ALL::contains).collect(Collectors.toCollection(LinkedHashSet::new));
        if (pipes.isEmpty()) {
            throw new IllegalArgumentException("PIPELINES không hợp lệ: " + list + " (chọn trong " + ALL + " hoặc all)");
        }
        return new StreamConfig(
                env.getOrDefault("KAFKA_BROKERS", "localhost:9094"),
                env.getOrDefault("KAFKA_SECURITY_PROTOCOL", "PLAINTEXT"),
                env.getOrDefault("KAFKA_SASL_MECHANISM", "SCRAM-SHA-512"),
                env.getOrDefault("KAFKA_USERNAME", ""),
                env.getOrDefault("KAFKA_PASSWORD", ""),
                env.getOrDefault("KAFKA_GROUP_PREFIX", "stream-processor"),
                pipes,
                Double.parseDouble(env.getOrDefault("STOCK_COVER_HOURS", "48")),
                Long.parseLong(env.getOrDefault("CHECKPOINT_INTERVAL_MS", "60000")));
    }

    public Properties kafkaProperties() {
        Properties p = new Properties();
        p.setProperty("security.protocol", securityProtocol);
        if (securityProtocol.startsWith("SASL")) {
            p.setProperty("sasl.mechanism", saslMechanism);
            p.setProperty("sasl.jaas.config", "org.apache.kafka.common.security.scram.ScramLoginModule required username=\""
                    + username + "\" password=\"" + password + "\";");
        }
        return p;
    }

    @Override
    public String toString() {
        return "StreamConfig[brokers=" + brokers + ", security=" + securityProtocol + ", pipelines=" + pipelines + "]";
    }
}
