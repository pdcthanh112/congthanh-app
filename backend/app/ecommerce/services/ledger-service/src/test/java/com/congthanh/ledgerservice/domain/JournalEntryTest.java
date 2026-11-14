package com.congthanh.ledgerservice.domain;

import com.congthanh.ledgerservice.domain.exception.LedgerException;
import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.AccountCode;
import com.congthanh.ledgerservice.domain.model.AccountType;
import com.congthanh.ledgerservice.domain.model.JournalEntry;
import com.congthanh.ledgerservice.domain.model.Posting;
import com.congthanh.ledgerservice.domain.model.Side;
import com.congthanh.ledgerservice.domain.posting.FinancialFact;
import com.congthanh.ledgerservice.domain.posting.PostingRules;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JournalEntryTest {

    private static final Instant NOW = Instant.parse("2026-11-14T03:30:00Z");

    @Test
    void unbalancedEntryIsRejected() {
        assertThatThrownBy(() -> new JournalEntry("e1", "manual", "m1", null, "x", LocalDate.now(), List.of(
                Posting.debit("asset:bank:operating", new BigDecimal("10.00"), "USD"),
                Posting.credit("revenue:gross_sales", new BigDecimal("9.99"), "USD")), "u", NOW))
                .isInstanceOf(LedgerException.class)
                .hasMessageContaining("unbalanced");
    }

    @Test
    void entryMustBalancePerCurrency() {
        assertThatThrownBy(() -> new JournalEntry("e1", "manual", "m1", null, "x", LocalDate.now(), List.of(
                Posting.debit("asset:bank:operating", new BigDecimal("10"), "USD"),
                Posting.credit("revenue:gross_sales", new BigDecimal("10"), "EUR")), "u", NOW))
                .isInstanceOf(LedgerException.class);
    }

    @Test
    void accountCodeDeterminesTypeAndNormalSide() {
        assertThat(AccountCode.of("liability:sales_tax_payable:NY").type()).isEqualTo(AccountType.LIABILITY);
        assertThat(AccountCode.of("contra_revenue:refunds").type().normalSide()).isEqualTo(Side.DEBIT);
        assertThatThrownBy(() -> AccountCode.of("cash")).isInstanceOf(LedgerException.class);
        assertThatThrownBy(() -> AccountCode.of("foo:bar")).isInstanceOf(LedgerException.class);

        var revenue = new AccountBalance(AccountCode.of("revenue:gross_sales"), "USD", new BigDecimal("5"), new BigDecimal("108"));
        assertThat(revenue.balance()).isEqualByComparingTo("103");
    }

    @Test
    void captureDebitsProviderClearingAndCreditsSalesOnNewYorkDate() {
        // 03:30 UTC ngày 14 là 22:30 ngày 13 ở New York
        JournalEntry e = PostingRules.post(new FinancialFact.PaymentCaptured("ev-1", NOW, "pay-1", "o-1", "Stripe",
                new BigDecimal("108.00"), "USD"), "e1", NOW);

        assertThat(e.bookingDate()).isEqualTo(LocalDate.of(2026, 11, 13));
        assertThat(e.sourceEventId()).isEqualTo("ev-1");
        assertThat(e.postings()).containsExactly(
                Posting.debit("asset:psp_clearing:stripe", new BigDecimal("108.0000"), "USD"),
                Posting.credit("revenue:gross_sales", new BigDecimal("108.0000"), "USD"));
    }

    @Test
    void refundMovesMoneyBackOutOfClearing() {
        JournalEntry e = PostingRules.post(new FinancialFact.PaymentRefunded("ev-2", NOW, "pay-1", "o-1", "paypal",
                "ret-1", new BigDecimal("54"), "USD"), "e2", NOW);

        assertThat(e.sourceType()).isEqualTo("refund");
        assertThat(e.sourceId()).isEqualTo("ret-1");
        assertThat(e.postings()).extracting(p -> p.account().value() + "/" + p.side())
                .containsExactly("contra_revenue:refunds/DEBIT", "asset:psp_clearing:paypal/CREDIT");
    }

    @Test
    void reversalFlipsSides() {
        JournalEntry e = PostingRules.post(new FinancialFact.PaymentCaptured("ev-1", NOW, "pay-1", "o-1", "stripe",
                new BigDecimal("10"), "USD"), "e1", NOW);
        JournalEntry r = e.reversal("e9", "duplicate", "fin-1", LocalDate.of(2026, 11, 14), NOW);

        assertThat(r.sourceId()).isEqualTo("e1");
        assertThat(r.postings()).extracting(Posting::side).containsExactly(Side.CREDIT, Side.DEBIT);
    }

    @Test
    void captureWithoutProviderCannotBePosted() {
        assertThatThrownBy(() -> PostingRules.post(new FinancialFact.PaymentCaptured("ev", NOW, "p", "o", "",
                BigDecimal.TEN, "USD"), "e", NOW)).isInstanceOf(LedgerException.class);
    }

    @Test
    void storeCreditFromReturnIsALiabilityFundedByRefunds() {
        JournalEntry e = PostingRules.post(new FinancialFact.StoredValueCredited("ev-3", NOW, "acc-1", "STORE_CREDIT",
                "RETURN_REFUND", "ret-9", new BigDecimal("25"), "USD"), "e3", NOW);
        assertThat(e.postings()).extracting(p -> p.account().value() + "/" + p.side())
                .containsExactly("contra_revenue:refunds/DEBIT", "liability:stored_value:store_credit/CREDIT");

        JournalEntry spent = PostingRules.post(new FinancialFact.StoredValueDebited("ev-4", NOW, "acc-1", "STORE_CREDIT",
                "h-1", "o-2", new BigDecimal("10"), "USD"), "e4", NOW);
        assertThat(spent.postings()).extracting(p -> p.account().value() + "/" + p.side())
                .containsExactly("liability:stored_value:store_credit/DEBIT", "revenue:gross_sales/CREDIT");

        assertThatThrownBy(() -> PostingRules.post(new FinancialFact.StoredValueCredited("ev", NOW, "a", "STORE_CREDIT",
                "MYSTERY", "r", BigDecimal.ONE, "USD"), "e", NOW)).isInstanceOf(LedgerException.class);
    }

    @Test
    void disputeMovesMoneyToDisputedFundsAndBookFeeThenSettles() {
        JournalEntry opened = PostingRules.post(new FinancialFact.DisputeOpened("ev-5", NOW, "d-1", "stripe", "o-1",
                new BigDecimal("80"), new BigDecimal("15"), "USD"), "e5", NOW);
        assertThat(opened.postings()).hasSize(4).extracting(p -> p.account().value())
                .contains("asset:disputed_funds:stripe", "expense:dispute_fees");

        JournalEntry won = PostingRules.post(new FinancialFact.DisputeClosed("ev-6", NOW, "d-1", "stripe", "WON",
                new BigDecimal("80"), "USD"), "e6", NOW);
        assertThat(won.postings().getFirst().account().value()).isEqualTo("asset:psp_clearing:stripe");

        JournalEntry lost = PostingRules.post(new FinancialFact.DisputeClosed("ev-7", NOW, "d-1", "stripe", "LOST",
                new BigDecimal("80"), "USD"), "e7", NOW);
        assertThat(lost.postings().getFirst().account().value()).isEqualTo("expense:chargeback_losses");
    }

    @Test
    void loyaltyPointsAreALiabilityFromConfirmationUntilSpentOrExpired() {
        JournalEntry confirmed = PostingRules.post(new FinancialFact.LoyaltyPoints("ev-8", NOW, "CONFIRMED", "c-1", "lot-1",
                250, new BigDecimal("2.50"), "USD"), "e8", NOW);
        assertThat(confirmed.postings()).extracting(p -> p.account().value() + "/" + p.side())
                .containsExactly("expense:loyalty_program/DEBIT", "liability:loyalty_points/CREDIT");

        JournalEntry redeemed = PostingRules.post(new FinancialFact.LoyaltyPoints("ev-9", NOW, "REDEEMED", "c-1", "rd-1",
                200, new BigDecimal("2.00"), "USD"), "e9", NOW);
        assertThat(redeemed.postings().get(1).account().value()).isEqualTo("revenue:gross_sales");

        JournalEntry expired = PostingRules.post(new FinancialFact.LoyaltyPoints("ev-10", NOW, "EXPIRED", "c-1", "lot-1",
                50, new BigDecimal("0.50"), "USD"), "e10", NOW);
        assertThat(expired.postings().get(1).account().value()).isEqualTo("expense:loyalty_program");
    }

    @Test
    void providerFeesPostOnlyTheDifferenceOnRerun() {
        JournalEntry first = PostingRules.post(new FinancialFact.ProviderFees("ev-11", NOW, "stripe", "2026-11-13",
                new BigDecimal("34.30"), BigDecimal.ZERO, "USD"), "e11", NOW);
        assertThat(first.postings()).extracting(p -> p.account().value() + "/" + p.side())
                .containsExactly("expense:psp_fees:stripe/DEBIT", "asset:psp_clearing:stripe/CREDIT");
        assertThat(first.bookingDate()).isEqualTo(LocalDate.of(2026, 11, 13));

        JournalEntry lower = PostingRules.post(new FinancialFact.ProviderFees("ev-12", NOW, "stripe", "2026-11-13",
                new BigDecimal("30.00"), new BigDecimal("34.30"), "USD"), "e12", NOW);
        assertThat(lower.postings().getFirst().side()).isEqualTo(Side.CREDIT);
        assertThat(lower.postings().getFirst().amount()).isEqualByComparingTo("4.30");
    }
}
