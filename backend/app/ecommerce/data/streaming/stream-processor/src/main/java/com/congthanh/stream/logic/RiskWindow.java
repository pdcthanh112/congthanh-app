package com.congthanh.stream.logic;

import com.congthanh.stream.model.OrderSignal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cửa sổ trượt 24 giờ các tín hiệu của một khách (state Flink, POJO). Mốc thời gian là event mới nhất đã thấy,
 * không phải đồng hồ máy: replay lại topic cho ra đúng feature như lúc chạy thật.
 */
public class RiskWindow {
    public static final long HOUR = 3_600_000L;
    public static final long DAY = 24 * HOUR;
    static final int MAX_ENTRIES = 2_000;

    public List<Entry> entries = new ArrayList<>();
    public long latest;
    public String lastCurrency = "";

    public static class Entry {
        public String kind = "";
        public long amountMinor;
        public String currency = "";
        public String addressFp = "";
        public long ts;

        public Entry() {}

        Entry(OrderSignal s) {
            kind = s.kind;
            amountMinor = s.amountMinor;
            currency = s.currency;
            addressFp = s.addressFp;
            ts = s.ts;
        }
    }

    public static class Features {
        public String customerId = "";
        public int orders1h;
        public long orderValue1h;
        public String currency = "";
        public int paymentFailures1h;
        public int cancellations24h;
        public int distinctAddresses24h;
        public long computedAt;
    }

    public void add(OrderSignal s) {
        if (s.ts < latest - DAY) {
            return; // quá cũ, đã ra khỏi cửa sổ
        }
        entries.add(new Entry(s));
        if (s.ts >= latest) {
            latest = s.ts;
        }
        if (OrderSignal.PLACED.equals(s.kind) && !s.currency.isEmpty()) {
            lastCurrency = s.currency;
        }
        evict();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    void evict() {
        long from = latest - DAY;
        entries.removeIf(e -> e.ts < from);
        if (entries.size() > MAX_ENTRIES) {
            entries.sort((a, b) -> Long.compare(a.ts, b.ts));
            entries = new ArrayList<>(entries.subList(entries.size() - MAX_ENTRIES, entries.size()));
        }
    }

    public Features features() {
        Features f = new Features();
        long hourFrom = latest - HOUR;
        Set<String> addresses = new HashSet<>();
        f.currency = lastCurrency;
        for (Entry e : entries) {
            boolean lastHour = e.ts >= hourFrom;
            switch (e.kind) {
                case OrderSignal.PLACED -> {
                    if (lastHour) {
                        f.orders1h++;
                        if (e.currency.equals(lastCurrency)) {
                            f.orderValue1h += e.amountMinor;
                        }
                    }
                    if (!e.addressFp.isEmpty()) {
                        addresses.add(e.addressFp);
                    }
                }
                case OrderSignal.PAYMENT_FAILED -> {
                    if (lastHour) {
                        f.paymentFailures1h++;
                    }
                }
                case OrderSignal.CANCELLED -> f.cancellations24h++;
                default -> {}
            }
        }
        f.distinctAddresses24h = addresses.size();
        f.computedAt = latest;
        return f;
    }
}
