package com.congthanh.checkoutservice.adapter.in.workflow;

import com.congthanh.ecommerce.workflow.checkout.CheckoutContract;
import com.congthanh.ecommerce.workflow.checkout.CheckoutInput;
import com.congthanh.ecommerce.workflow.checkout.CheckoutResult;
import com.congthanh.ecommerce.workflow.checkout.CheckoutState;
import com.congthanh.ecommerce.workflow.checkout.PaymentCompleted;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface CheckoutWorkflow {

    @WorkflowMethod(name = CheckoutContract.WORKFLOW_TYPE)
    CheckoutResult run(CheckoutInput input);

    @SignalMethod(name = CheckoutContract.SIGNAL_PAYMENT_COMPLETED)
    void paymentCompleted(PaymentCompleted result);

    @SignalMethod(name = CheckoutContract.SIGNAL_CANCEL)
    void cancel(String reason);

    @QueryMethod(name = CheckoutContract.QUERY_STATUS)
    CheckoutState status();
}
