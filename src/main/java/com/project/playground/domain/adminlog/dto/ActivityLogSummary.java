package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.LocalDateTime;

/** 목록에 필요한 활동·결과·접속 정보만 선택한다. 입력 비밀번호와 원문 부가 JSON은 반환하지 않는다. */
@Getter @Builder
public class ActivityLogSummary {
    private final Long id;
    private final LocalDateTime occurredAt;
    private final LogPerson member;
    private final String type;
    private final String typeName;
    private final String result;
    private final String resultName;
    private final String clientIp;
    private final String failureCode;
    private final String failureMessage;
}
