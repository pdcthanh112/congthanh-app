package com.congthanh.checkoutservice.adapter.out.persistence;

import com.congthanh.checkoutservice.domain.model.Address;
import com.congthanh.checkoutservice.domain.model.CheckoutSession;
import com.congthanh.checkoutservice.domain.model.Money;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** Document JSONB đọc lại phải ra đúng phiên (Money giữ scale theo tiền tệ, Instant, record lồng nhau). */
class SnapshotJsonTest {

    @Test
    void roundTrip() {
        var now = Instant.parse("2026-11-10T10:00:00Z");
        var s = CheckoutSession.open("s-1", "o-1", "c-1", List.of("VIP"), "USD", List.of(new CheckoutSession.Item("p-1", 2)),
                now, Duration.ofMinutes(30));
        s.changeAddresses(new Address("A", "1", "L", null, null, null, "CA", "US", null), null);
        s.applyQuote(new CheckoutSession.Quote(
                List.of(new CheckoutSession.PricedLine("p-1", "S", "N", 2, Money.of("10", "USD"), Money.of("12", "USD"),
                        Money.of("20", "USD"), 500, 1)),
                List.of(), Money.of("5", "USD"), null,
                List.of(new CheckoutSession.ShippingOption("internal", "standard", "Std", Money.of("3", "USD"), 1, 2)),
                Map.of("p-1", Money.of("2", "USD"))), now, Duration.ofMinutes(30));

        JsonMapper json = JsonMapper.builder().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        String text = json.writeValueAsString(s.snapshot());
        var back = CheckoutSession.restore(json.readValue(text, CheckoutSession.Snapshot.class));

        assertThat(back.snapshot()).isEqualTo(s.snapshot());
        assertThat(back.totals().total()).isEqualTo(Money.of("20", "USD"));
        assertThat(back.selected().key()).isEqualTo("internal:standard");
    }
}
