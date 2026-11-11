package com.congthanh.stream.pipeline;

import com.congthanh.ecommerce.stream.v1.StockCover;
import com.congthanh.stream.StreamConfig;
import com.congthanh.stream.io.Envelopes;
import com.congthanh.stream.logic.StockCoverState;
import com.congthanh.stream.model.InventorySignal;
import com.congthanh.stream.model.KeyedRecord;
import java.time.Duration;
import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/** inventory events → độ phủ tồn kho (giờ còn bán được) và cờ nguy cơ hết hàng (ecommerce.stream.stock-cover). */
public final class StockCoverPipeline {
    private StockCoverPipeline() {}

    public static DataStream<KeyedRecord> build(DataStream<byte[]> raw, double thresholdHours) {
        DataStream<InventorySignal> signals = raw
                .flatMap((byte[] b, Collector<InventorySignal> out) -> Envelopes.inventorySignals(b, System.currentTimeMillis()).forEach(out::collect))
                .returns(InventorySignal.class)
                .name("decode-inventory").uid("stock-decode");
        return cover(signals, thresholdHours);
    }

    public static DataStream<KeyedRecord> cover(DataStream<InventorySignal> signals, double thresholdHours) {
        return signals
                .keyBy(s -> s.productId)
                .process(new Cover(thresholdHours)).name("stock-cover").uid("stock-cover");
    }

    static final class Cover extends KeyedProcessFunction<String, InventorySignal, KeyedRecord> {
        private final double thresholdHours;
        private transient ValueState<StockCoverState> state;

        Cover(double thresholdHours) {
            this.thresholdHours = thresholdHours;
        }

        @Override
        public void open(OpenContext ctx) {
            ValueStateDescriptor<StockCoverState> d = new ValueStateDescriptor<>("stock", TypeInformation.of(new TypeHint<StockCoverState>() {}));
            d.enableTimeToLive(StateTtlConfig.newBuilder(Duration.ofDays(30)).build());
            state = getRuntimeContext().getState(d);
        }

        @Override
        public void processElement(InventorySignal s, Context ctx, Collector<KeyedRecord> out) throws Exception {
            StockCoverState st = state.value();
            if (st == null) {
                st = new StockCoverState();
            }
            StockCoverState.Cover c = st.apply(s, thresholdHours);
            state.update(st);
            String productId = ctx.getCurrentKey();
            StockCover msg = StockCover.newBuilder()
                    .setProductId(productId)
                    .setAvailable(c.available)
                    .setUnitsSold24H(c.unitsSold24h)
                    .setHoursOfCover(c.hoursOfCover)
                    .setAtRisk(c.atRisk)
                    .setRiskChanged(c.riskChanged)
                    .setComputedAt(Envelopes.ts(c.computedAt))
                    .build();
            out.collect(new KeyedRecord(StreamConfig.OUT_STOCK, productId, Envelopes.wrap(msg, "product", productId, c.computedAt)));
        }
    }
}
