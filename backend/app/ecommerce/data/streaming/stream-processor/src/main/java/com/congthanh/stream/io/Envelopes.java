package com.congthanh.stream.io;

import com.congthanh.cartservice.grpc.CartItemAdded;
import com.congthanh.customerservice.grpc.WishlistItemAdded;
import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.common.v1.Money;
import com.congthanh.ecommerce.order.v1.OrderCancelled;
import com.congthanh.ecommerce.order.v1.OrderConfirmed;
import com.congthanh.ecommerce.order.v1.OrderLine;
import com.congthanh.ecommerce.order.v1.OrderPlaced;
import com.congthanh.ecommerce.order.v1.ShippingAddress;
import com.congthanh.ecommerce.payment.v1.PaymentFailed;
import com.congthanh.inventoryservice.grpc.InventoryCommitted;
import com.congthanh.inventoryservice.grpc.ReservedLine;
import com.congthanh.inventoryservice.grpc.StockLevelChanged;
import com.congthanh.stream.model.InteractionSignal;
import com.congthanh.stream.model.InventorySignal;
import com.congthanh.stream.model.OrderSignal;
import com.google.protobuf.Any;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Message;
import com.google.protobuf.Timestamp;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class Envelopes {
    public static final String SOURCE = "stream-processor";
    private static final Set<String> ZERO_DECIMAL = Set.of("VND", "JPY", "KRW");

    private Envelopes() {}

    public static EventEnvelope parse(byte[] raw) {
        if (raw == null || raw.length == 0) {
            return null;
        }
        try {
            return EventEnvelope.parseFrom(raw);
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    static long timestamp(EventEnvelope env, long fallback) {
        if (!env.hasOccurredAt()) {
            return fallback;
        }
        Timestamp t = env.getOccurredAt();
        return t.getSeconds() * 1000 + t.getNanos() / 1_000_000;
    }

    static <T extends Message> T unpack(EventEnvelope env, Class<T> type) {
        try {
            return env.getPayload().unpack(type);
        } catch (InvalidProtocolBufferException e) {
            return null;
        }
    }

    static boolean is(EventEnvelope env, Message proto) {
        return proto.getDescriptorForType().getFullName().equals(env.getType());
    }

    /** Đơn vị nhỏ nhất của tiền tệ (USD/EUR là cent; JPY, KRW, VND không có số lẻ), giống fraud-service. */
    public static long toMinor(Money m) {
        int digits = ZERO_DECIMAL.contains(m.getCurrencyCode()) ? 0 : 2;
        long scale = digits == 0 ? 1 : 100;
        return m.getUnits() * scale + Math.round(m.getNanos() / Math.pow(10, 9 - digits));
    }

    public static String addressFingerprint(ShippingAddress a) {
        if (a == null || a.getCountryCode().isEmpty()) {
            return "";
        }
        String joined = String.join("|", a.getCountryCode(), a.getRegionCode(), a.getLine1(), a.getPhone())
                .toLowerCase(Locale.ROOT).strip();
        return sha256(joined).substring(0, 24);
    }

    static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static List<OrderSignal> orderSignals(byte[] raw, long now) {
        EventEnvelope env = parse(raw);
        List<OrderSignal> out = new ArrayList<>(1);
        if (env == null) {
            return out;
        }
        long ts = timestamp(env, now);
        if (is(env, OrderPlaced.getDefaultInstance())) {
            OrderPlaced ev = unpack(env, OrderPlaced.class);
            if (ev != null && !ev.getCustomerId().isEmpty()) {
                String fp = ev.hasShippingAddress() ? addressFingerprint(ev.getShippingAddress()) : "";
                out.add(new OrderSignal(ev.getOrderId(), ev.getCustomerId(), OrderSignal.PLACED,
                        ev.hasTotal() ? toMinor(ev.getTotal()) : 0, ev.getTotal().getCurrencyCode(), fp, ts));
            }
        } else if (is(env, OrderConfirmed.getDefaultInstance())) {
            OrderConfirmed ev = unpack(env, OrderConfirmed.class);
            if (ev != null) {
                out.add(new OrderSignal(ev.getOrderId(), "", OrderSignal.CONFIRMED, 0, "", "", ts));
            }
        } else if (is(env, OrderCancelled.getDefaultInstance())) {
            OrderCancelled ev = unpack(env, OrderCancelled.class);
            if (ev != null) {
                out.add(new OrderSignal(ev.getOrderId(), "", OrderSignal.CANCELLED, 0, "", "", ts));
            }
        } else if (is(env, PaymentFailed.getDefaultInstance())) {
            PaymentFailed ev = unpack(env, PaymentFailed.class);
            if (ev != null) {
                out.add(new OrderSignal(ev.getOrderId(), "", OrderSignal.PAYMENT_FAILED, 0, "", "", ts));
            }
        }
        return out;
    }

    public static List<InteractionSignal> interactionSignals(byte[] raw, long now) {
        EventEnvelope env = parse(raw);
        List<InteractionSignal> out = new ArrayList<>();
        if (env == null) {
            return out;
        }
        long ts = timestamp(env, now);
        if (is(env, OrderPlaced.getDefaultInstance())) {
            OrderPlaced ev = unpack(env, OrderPlaced.class);
            if (ev != null) {
                for (OrderLine l : ev.getLinesList()) {
                    out.add(new InteractionSignal(l.getProductId(), InteractionSignal.ORDER, l.getQuantity(), ts));
                }
            }
        } else if (is(env, CartItemAdded.getDefaultInstance())) {
            CartItemAdded ev = unpack(env, CartItemAdded.class);
            if (ev != null) {
                out.add(new InteractionSignal(ev.getProductId(), InteractionSignal.CART, ev.getQuantity(), ts));
            }
        } else if (is(env, WishlistItemAdded.getDefaultInstance())) {
            WishlistItemAdded ev = unpack(env, WishlistItemAdded.class);
            if (ev != null) {
                out.add(new InteractionSignal(ev.getProductId(), InteractionSignal.WISHLIST, 1, ts));
            }
        }
        out.removeIf(s -> s.productId.isEmpty());
        return out;
    }

    public static List<InventorySignal> inventorySignals(byte[] raw, long now) {
        EventEnvelope env = parse(raw);
        List<InventorySignal> out = new ArrayList<>();
        if (env == null) {
            return out;
        }
        long ts = timestamp(env, now);
        if (is(env, StockLevelChanged.getDefaultInstance())) {
            StockLevelChanged ev = unpack(env, StockLevelChanged.class);
            if (ev != null) {
                out.add(new InventorySignal(ev.getProductId(), InventorySignal.LEVEL, ev.getWarehouseId(), ev.getAvailable(), ts));
            }
        } else if (is(env, InventoryCommitted.getDefaultInstance())) {
            InventoryCommitted ev = unpack(env, InventoryCommitted.class);
            if (ev != null) {
                for (ReservedLine l : ev.getLinesList()) {
                    out.add(new InventorySignal(l.getProductId(), InventorySignal.SOLD, l.getWarehouseId(), l.getQuantity(), ts));
                }
            }
        }
        out.removeIf(s -> s.productId.isEmpty());
        return out;
    }

    public static Timestamp ts(long millis) {
        return Timestamp.newBuilder().setSeconds(Math.floorDiv(millis, 1000)).setNanos((int) Math.floorMod(millis, 1000) * 1_000_000).build();
    }

    /** id tất định theo (type, key, thời điểm): chạy lại từ checkpoint sinh cùng id, consumer tự khử trùng. */
    public static byte[] wrap(Message payload, String aggregateType, String key, long at) {
        String type = payload.getDescriptorForType().getFullName();
        String id = UUID.nameUUIDFromBytes((type + "|" + key + "|" + at).getBytes(StandardCharsets.UTF_8)).toString();
        return EventEnvelope.newBuilder()
                .setId(id)
                .setType(type)
                .setSource(SOURCE)
                .setAggregateType(aggregateType)
                .setAggregateId(key)
                .setOccurredAt(ts(at))
                .setPayload(Any.pack(payload))
                .build()
                .toByteArray();
    }
}
