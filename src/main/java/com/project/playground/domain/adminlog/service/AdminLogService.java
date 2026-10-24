package com.project.playground.domain.adminlog.service;

import com.project.playground.domain.adminlog.dto.*;
import com.project.playground.domain.adminlog.repository.AdminLogQueryRepository;
import com.project.playground.domain.adminlog.support.*;
import com.project.playground.domain.activitylog.enums.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 두 종류의 조회 권한을 독립 검사한다. 읽기 트랜잭션의 같은 스냅샷에서 집계와 페이지를 조회한다. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminLogService {
    private final AdminAuthorizationService authorization;
    private final AdminLogQueryRepository queries;
    private final AdminLogDetailMapper details;

    public LogOptions activityOptions(Long actor) { authorization.require(actor, PermissionCode.ACTIVITY_LOG_READ); return AdminLogCatalog.activities(); }
    public LogOptions auditOptions(Long actor) { authorization.require(actor, PermissionCode.ADMIN_AUDIT_READ); return AdminLogCatalog.audits(); }
    public LogPageResponse<ActivityLogSummary> activities(Long actor, LogSearch search) {
        authorization.require(actor, PermissionCode.ACTIVITY_LOG_READ);
        try {
            if (search.getType() != null) ActivityType.valueOf(search.getType());
            if (search.getResult() != null) ActivityResult.valueOf(search.getResult());
        } catch (IllegalArgumentException ignored) { throw new BusinessException(ErrorCode.VALIDATION_FAILED); }
        return queries.activities(search);
    }
    public LogPageResponse<AuditLogSummary> audits(Long actor, LogSearch search) { authorization.require(actor, PermissionCode.ADMIN_AUDIT_READ); return queries.audits(search); }
    public ActivityLogDetails activity(Long actor, long id) { authorization.require(actor, PermissionCode.ACTIVITY_LOG_READ); return details.activity(queries.activity(id)); }
    public AuditLogDetails audit(Long actor, long id) {
        authorization.require(actor, PermissionCode.ADMIN_AUDIT_READ);
        var source = queries.audit(id);
        return details.audit(source, queries.roleNames(details.roleCodes(source.getDetail())));
    }
}
