package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 변경 내용·기존 사유를 조회할 뿐 기록 수정·삭제·되돌리기 명령은 제공하지 않는다. */
@Getter @Builder
public class AuditLogDetails {
    private final AuditLogSummary entry;
    private final List<LogChange> changes;
    private final List<LogAttribute> attributes;
}
