package com.congthanh.productservice.domain.support;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SnowflakeIdGenerator {

    private final long epoch = 1609459200000L; // Thời điểm bắt đầu (2021-01-01T00:00:00Z)
    private final long workerIdBits = 5L; // Số bit dành cho worker ID
    private final long datacenterIdBits = 5L; // Số bit dành cho datacenter ID
    private final long sequenceBits = 12L; // Số bit dành cho số sequence

    private final long maxWorkerId = -1L ^ (-1L << workerIdBits);
    private final long maxDatacenterId = -1L ^ (-1L << datacenterIdBits);
    private final long sequenceMask = -1L ^ (-1L << sequenceBits);

    private final long workerIdShift = sequenceBits;
    private final long datacenterIdShift = sequenceBits + workerIdBits;
    private final long timestampLeftShift = sequenceBits + workerIdBits + datacenterIdBits;

    private long lastTimestamp = -1L;
    private long sequence = 0L;

//    @Value("${snowflake.worker-id}")
//    private final long workerId ;
//
//    @Value("${snowflake.datacenter-id}")
//    private final long datacenterId;

    private final long workerId;
    private final long datacenterId;

    //    public SnowflakeIdGenerator(long workerId, long datacenterId) {
    public SnowflakeIdGenerator(
            @Value("${snowflake.worker-id}") long workerId,
            @Value("${snowflake.datacenter-id}") long datacenterId) {
        if (workerId > maxWorkerId || workerId < 0) {
            throw new IllegalArgumentException(String.format("Worker ID can't be greater than %d or less than 0", maxWorkerId));
        }
        if (datacenterId > maxDatacenterId || datacenterId < 0) {
            throw new IllegalArgumentException(String.format("Datacenter ID can't be greater than %d or less than 0", maxDatacenterId));
        }
        this.workerId = workerId;
        this.datacenterId = datacenterId;
    }

    public synchronized long nextId() {
        long timestamp = timeGen();

        if (timestamp < lastTimestamp) {
            throw new RuntimeException("Clock moved backwards. Refusing to generate ID.");
        }

        if (lastTimestamp == timestamp) {
            sequence = (sequence + 1) & sequenceMask;
            if (sequence == 0) {
                timestamp = tilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;

        return ((timestamp - epoch) << timestampLeftShift) |
                (datacenterId << datacenterIdShift) |
                (workerId << workerIdShift) |
                sequence;
    }

    private long tilNextMillis(long lastTimestamp) {
        long timestamp = timeGen();
        while (timestamp <= lastTimestamp) {
            timestamp = timeGen();
        }
        return timestamp;
    }

    private long timeGen() {
        return System.currentTimeMillis();
    }

}
