package com.congthanh.returnsservice.application.port.in;

import com.congthanh.returnsservice.domain.model.RefundMethod;
import com.congthanh.returnsservice.domain.model.ReturnLine;
import com.congthanh.returnsservice.domain.model.ReturnRequest;

import java.util.List;

public interface CustomerReturns {

    ReturnRequest open(String customerId, String orderId, List<ReturnLine> lines, RefundMethod method);

    List<ReturnRequest> mine(String customerId);

    /** @param admin true khi người gọi là ADMIN/SUPPORT: xem được yêu cầu của mọi khách */
    ReturnRequest get(String returnId, String callerId, boolean admin);

    void cancel(String returnId, String customerId);
}
