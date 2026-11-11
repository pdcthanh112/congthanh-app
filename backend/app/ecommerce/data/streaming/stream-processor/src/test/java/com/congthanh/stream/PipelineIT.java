package com.congthanh.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.congthanh.ecommerce.common.v1.EventEnvelope;
import com.congthanh.ecommerce.stream.v1.ProductTrending;
import com.congthanh.stream.logic.RiskWindow;
import com.congthanh.stream.model.InteractionSignal;
import com.congthanh.stream.model.KeyedRecord;
import com.congthanh.stream.model.OrderSignal;
import com.congthanh.stream.pipeline.CustomerRiskPipeline;
import com.congthanh.stream.pipeline.ProductTrendingPipeline;
import java.util.ArrayList;
import java.util.List;
import org.apache.flink.runtime.testutils.MiniClusterResourceConfiguration;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.junit5.MiniClusterExtension;
import org.apache.flink.util.CloseableIterator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Chạy pipeline thật trên MiniCluster (state, keyBy, window, serializer POJO), không cần Kafka. */
class PipelineIT {
    @RegisterExtension
    static final MiniClusterExtension FLINK = new MiniClusterExtension(
            new MiniClusterResourceConfiguration.Builder().setNumberTaskManagers(1).setNumberSlotsPerTaskManager(2).build());

    static <T> List<T> collect(CloseableIterator<T> it) throws Exception {
        List<T> out = new ArrayList<>();
        try (it) {
            it.forEachRemaining(out::add);
        }
        return out;
    }

    @Test
    void paymentFailureArrivingBeforeOrderIsAttributedToCustomer() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        List<RiskWindow.Features> out = collect(CustomerRiskPipeline.features(env.fromData(
                new OrderSignal("o1", "", OrderSignal.PAYMENT_FAILED, 0, "", "", 1_000),
                new OrderSignal("o1", "c1", OrderSignal.PLACED, 500, "USD", "fp", 900),
                new OrderSignal("o2", "c1", OrderSignal.PLACED, 700, "USD", "fp2", 2_000),
                new OrderSignal("o2", "", OrderSignal.PAYMENT_FAILED, 0, "", "", 2_100))).executeAndCollect());

        RiskWindow.Features last = out.getLast();
        assertThat(last.customerId).isEqualTo("c1");
        assertThat(last.orders1h).isEqualTo(2);
        assertThat(last.orderValue1h).isEqualTo(1_200);
        assertThat(last.paymentFailures1h).isEqualTo(2);
        assertThat(last.distinctAddresses24h).isEqualTo(2);
    }

    @Test
    void trendingWindowsScoreInteractions() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);
        long t = 10 * 60_000L;
        List<KeyedRecord> out = collect(ProductTrendingPipeline.windowed(env.fromData(
                new InteractionSignal("p1", InteractionSignal.CART, 1, t),
                new InteractionSignal("p1", InteractionSignal.ORDER, 2, t + 1),
                new InteractionSignal("p2", InteractionSignal.WISHLIST, 1, t + 2))).executeAndCollect());

        // Cửa sổ 1 giờ bước 5 phút → mỗi sản phẩm có 12 cửa sổ chứa event; lấy cửa sổ đầu tiên của p1.
        ProductTrending p1 = EventEnvelope.parseFrom(out.stream().filter(r -> r.key.equals("p1")).findFirst().orElseThrow().value)
                .getPayload().unpack(ProductTrending.class);
        assertThat(out.stream().filter(r -> r.key.equals("p1"))).hasSize(12);
        assertThat(p1.getScore()).isEqualTo(3 + 5 + 2); // cart + order + 2 units
        assertThat(p1.getUnits()).isEqualTo(2);
        assertThat(out).allSatisfy(r -> assertThat(r.topic).isEqualTo(StreamConfig.OUT_TRENDING));
    }
}
