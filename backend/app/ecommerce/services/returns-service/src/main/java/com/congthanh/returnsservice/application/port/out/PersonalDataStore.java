package com.congthanh.returnsservice.application.port.out;

public interface PersonalDataStore {

    /** Ẩn danh dữ liệu của khách; trả lý do pháp lý cho phần phải giữ lại, null nếu đã xoá hết. */
    String anonymize(String subjectId);

    /** Ghi ErasureCompleted vào outbox (aggregatetype "privacy") trong cùng transaction với việc xoá. */
    void confirmErasure(String requestId, String subjectId, String retentionReason);
}
