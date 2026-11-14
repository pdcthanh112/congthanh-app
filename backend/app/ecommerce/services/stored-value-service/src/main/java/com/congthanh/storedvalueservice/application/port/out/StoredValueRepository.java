package com.congthanh.storedvalueservice.application.port.out;

import com.congthanh.storedvalueservice.domain.model.Account;
import com.congthanh.storedvalueservice.domain.model.Hold;
import com.congthanh.storedvalueservice.domain.model.Movement;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface StoredValueRepository {

    /** Khoá hàng tài khoản (SELECT FOR UPDATE) tới hết transaction. */
    Optional<Account> lockAccount(String customerId, Account.Kind kind, String currency);

    Optional<Account> lockAccountById(String accountId);

    List<Account> byCustomer(String customerId);

    /** Lưu tài khoản (thêm mới hoặc cập nhật), ghi các movement và event vào outbox trong cùng transaction. */
    void save(Account account);

    Optional<Movement> findMovement(Movement.Type type, String reference);

    List<Movement> history(String accountId, int limit);

    Optional<Hold> lockHold(String holdId);

    void saveHold(Hold hold);

    List<Hold> expiredHolds(Instant now, int limit);
}
