package com.congthanh.ledgerservice.application.port.in;

import com.congthanh.ledgerservice.domain.model.AccountBalance;
import com.congthanh.ledgerservice.domain.model.JournalEntry;

import java.time.LocalDate;
import java.util.List;

public interface LedgerQueries {

    List<AccountBalance> balance(String account);

    List<JournalEntry> entriesBySource(String sourceType, String sourceId);

    List<JournalEntry> entriesByAccount(String account, LocalDate from, LocalDate to, int limit);

    /** Bảng cân đối thử tới hết ngày {@code asOf}: mọi tài khoản, tổng Nợ phải bằng tổng Có theo tiền tệ. */
    TrialBalance trialBalance(LocalDate asOf);

    record TrialBalance(LocalDate asOf, List<AccountBalance> accounts, boolean balanced) {
    }
}
