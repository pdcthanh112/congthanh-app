package com.congthanh.commonservice.persistence;

import com.congthanh.commonservice.security.AuthenticationUtils;
import org.springframework.data.domain.AuditorAware;

import java.util.Optional;

/** created_by / updated_by lấy từ người đang đăng nhập; job nền và consumer không có người dùng thì ghi tên hệ thống. */
public class SecurityAuditorAware implements AuditorAware<String> {

    private final String systemAuditor;

    public SecurityAuditorAware(String systemAuditor) {
        this.systemAuditor = systemAuditor;
    }

    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of(AuthenticationUtils.currentUserId().orElse(systemAuditor));
    }
}
