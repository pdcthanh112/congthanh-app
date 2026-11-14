package com.congthanh.returnsservice.application.port.in;

import com.congthanh.returnsservice.domain.model.ItemCondition;
import com.congthanh.returnsservice.domain.model.ReturnRequest;
import com.congthanh.returnsservice.domain.model.ReturnStatus;

import java.util.List;
import java.util.Map;

public interface ReturnAdmin {

    ReturnRequest approve(String returnId, String by);

    ReturnRequest reject(String returnId, String by, String reason);

    /** Kho nhận và kiểm hàng; có tiền phải hoàn thì khởi chạy workflow hoàn tiền. */
    ReturnRequest receive(String returnId, Map<String, ItemCondition> conditions, String by);

    ReturnRequest retryRefund(String returnId, String by);

    List<ReturnRequest> byStatus(ReturnStatus status, int limit);

    /** Khởi chạy lại workflow cho yêu cầu REFUND_PENDING bị treo (pod chết sau commit, trước khi start). */
    int resumeStuckRefunds();
}
