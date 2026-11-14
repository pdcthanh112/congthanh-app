package com.congthanh.disputeservice.application.port.in;

import com.congthanh.disputeservice.domain.model.Dispute;
import com.congthanh.disputeservice.domain.model.DisputeStatus;

import java.util.List;
import java.util.Map;

/** Bàn làm việc của người xử lý tranh chấp (team tài chính/CSKH). */
public interface DisputeDesk {

    List<Dispute> byStatus(DisputeStatus status, int limit);

    Dispute get(String id);

    /** Gom lại bằng chứng từ dữ liệu mới nhất (vận đơn vừa cập nhật, vừa hoàn tiền...). */
    Dispute refreshEvidence(String id);

    /** Nộp bằng chứng; {@code extraFields} cho người xử lý bổ sung/ghi đè trường. */
    Dispute submit(String id, Map<String, String> extraFields, String by);

    Dispute accept(String id, String why, String by);

    /** Tranh chấp còn mở sắp tới hạn (cảnh báo). */
    List<Dispute> dueSoon();
}
