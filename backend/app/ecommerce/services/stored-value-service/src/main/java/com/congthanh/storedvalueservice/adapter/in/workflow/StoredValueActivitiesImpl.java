package com.congthanh.storedvalueservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.storedvalue.StoredValueActivities;
import com.congthanh.storedvalueservice.application.port.in.StoredValueUseCases;
import com.congthanh.storedvalueservice.domain.exception.StoredValueException;
import com.congthanh.storedvalueservice.domain.model.Movement;
import io.temporal.failure.ApplicationFailure;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class StoredValueActivitiesImpl implements StoredValueActivities {

    private final StoredValueUseCases storedValue;

    @Override
    public Result credit(CreditRequest r) {
        return result(() -> storedValue.credit(r.customerId(), r.amount(), r.currency(), r.reason(), r.reference()));
    }

    @Override
    public Result hold(HoldRequest r) {
        return result(() -> storedValue.hold(r.customerId(), r.holdId(), r.orderId(), r.amount(), r.currency()));
    }

    @Override
    public void capture(String holdId) {
        result(() -> {
            storedValue.capture(holdId);
            return null;
        });
    }

    @Override
    public void release(String holdId, String reason) {
        storedValue.release(holdId, reason);
    }

    private static Result result(Supplier<Movement> call) {
        try {
            Movement m = call.get();
            return m == null ? null : new Result(m.accountId(), m.balanceAfter());
        } catch (StoredValueException e) {
            if (e.kind() == StoredValueException.Kind.INSUFFICIENT_FUNDS || e.kind() == StoredValueException.Kind.INVALID
                    || e.kind() == StoredValueException.Kind.CONFLICT || e.kind() == StoredValueException.Kind.NOT_FOUND) {
                throw ApplicationFailure.newNonRetryableFailure(e.getMessage(), REJECTED);
            }
            throw e;
        }
    }
}
