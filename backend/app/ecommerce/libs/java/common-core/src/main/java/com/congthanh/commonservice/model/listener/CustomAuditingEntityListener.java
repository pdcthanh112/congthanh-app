package com.congthanh.commonservice.model.listener;

import com.congthanh.commonservice.model.BaseEntity;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.data.auditing.AuditingHandler;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Configurable
public class CustomAuditingEntityListener extends AuditingEntityListener {

    public CustomAuditingEntityListener(ObjectFactory<AuditingHandler> handler) {
        super.setAuditingHandler(handler);
    }

    @Override
    @PrePersist
    public void touchForCreate(@NotNull Object target) {
        BaseEntity entity = (BaseEntity) target;
        if (entity.getCreatedBy() == null) {
            super.touchForCreate(target);
        } else {
            if (entity.getUpdatedBy() == null) {
                entity.setUpdatedBy(entity.getCreatedBy());
            }
        }
    }

    @Override
    @PreUpdate
    public void touchForUpdate(@NotNull Object target) {
        BaseEntity entity = (BaseEntity) target;
        if (entity.getUpdatedBy() == null) {
            super.touchForUpdate(target);
        }
    }

}
