package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 선택한 활동의 브라우저와 허용된 입력 식별 정보만 상세에서 제공한다. */
@Getter @Builder
public class ActivityLogDetails {
    private final ActivityLogSummary entry;
    private final String userAgent;
    private final List<LogAttribute> attributes;
    private final LoginLogDiagnostics loginDiagnostics;
}
