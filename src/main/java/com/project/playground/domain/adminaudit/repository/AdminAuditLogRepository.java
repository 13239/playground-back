package com.project.playground.domain.adminaudit.repository;

import com.project.playground.domain.adminaudit.entity.AdminAuditLog;
import org.springframework.data.repository.Repository;

/** 기록 저장에 필요한 메서드만 노출해 범용 수정/삭제 경로를 만들지 않는다. */
public interface AdminAuditLogRepository extends Repository<AdminAuditLog, Long> {
    AdminAuditLog save(AdminAuditLog audit);
}

