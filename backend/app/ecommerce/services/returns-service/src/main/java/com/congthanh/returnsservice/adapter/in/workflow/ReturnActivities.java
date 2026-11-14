package com.congthanh.returnsservice.adapter.in.workflow;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface(namePrefix = "Returns.")
public interface ReturnActivities {

    @ActivityMethod
    void markRefunded(String returnId, String paymentId);

    @ActivityMethod
    void markRefundFailed(String returnId, String reason);
}
