package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

/** 작업 당시 권한 코드는 저장된 값을 사용하며 현재 표시 이름과 함께 반환한다. */
@Getter @Builder(toBuilder = true)
public class AuditLogSummary {
    private final Long id;
    private final LocalDateTime occurredAt;
    private final LogPerson actor;
    private final String actorRoleCode;
    private final String actorRoleName;
    private final String action;
    private final String actionName;
    private final String targetType;
    private final String targetId;
    private final String targetName;
    private final String reason;
}
