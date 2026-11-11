package com.congthanh.stream.model;

/** Bản ghi đã tuần tự hoá sẵn để ghi Kafka: key = id thực thể (topic compacted), value = EventEnvelope. */
public class KeyedRecord {
    public String topic = "";
    public String key = "";
    public byte[] value = new byte[0];

    public KeyedRecord() {}

    public KeyedRecord(String topic, String key, byte[] value) {
        this.topic = topic;
        this.key = key;
        this.value = value;
    }
}
