package com.congthanh.orderservice.application.port.in;

/** Khách huỷ đơn khi saga chưa xong (chưa thanh toán): gửi signal cancel tới workflow. */
public interface CancelOrderUseCase {

    void requestCancel(String orderCode, Requester requester, String reason);
}
