package com.congthanh.platform.inbox;

import com.congthanh.ecom.common.v1.EventEnvelope;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;

import java.util.Optional;

/** Giải mã record Kafka (value = EventEnvelope protobuf) do outbox của bất kỳ service nào phát ra. */
public final class EventEnvelopes {

    private EventEnvelopes() {
    }

    public static EventEnvelope parse(byte[] value) {
        try {
            return EventEnvelope.parseFrom(value);
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("record is not an ecommerce.common.v1.EventEnvelope", e);
        }
    }

    /** Mở payload nếu đúng kiểu, ngược lại trả về empty (event khác trên cùng topic). */
    public static <T extends Message> Optional<T> unpack(EventEnvelope envelope, Class<T> type) {
        if (!envelope.getPayload().is(type)) {
            return Optional.empty();
        }
        try {
            return Optional.of(envelope.getPayload().unpack(type));
        } catch (InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("bad payload for " + envelope.getType(), e);
        }
    }
}
