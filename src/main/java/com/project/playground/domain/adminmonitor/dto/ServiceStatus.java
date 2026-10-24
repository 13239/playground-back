package com.project.playground.domain.adminmonitor.dto;

import lombok.Builder;
import lombok.Getter;
import java.time.Instant;

/** 상태는 아이콘·텍스트로 표시할 고정 코드만 공개하며 내부 진단·주소·오류 원문은 제외한다. */
@Getter @Builder
public class ServiceStatus {
    private final String code;
    private final String state;
    private final Instant checkedAt;
}
