package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 관리자 권한을 확인한 상세 응답에만 포함하는 로그인 진단이다. 원문 JSON은 제공하지 않는다. */
@Getter @Builder
public class LoginLogDiagnostics {
    private final String attemptId;
    private final String source;
    private final String stage;
    private final String stageName;
    private final String reason;
    private final String reasonName;
    private final List<LogAttribute> context;
    private final List<Step> steps;

    @Getter @Builder
    public static class Step {
        private final String stage;
        private final String stageName;
        private final String outcome;
        private final long durationMillis;
    }
}
