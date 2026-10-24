package com.project.playground.domain.adminlog.dto.internal;

import lombok.Builder;
import lombok.Getter;

/** 저장소와 상세 매퍼 사이의 내부 자료다. 원문 JSON을 컨트롤러 응답으로 사용하지 않는다. */
@Getter @Builder
public class LogDetailSource<T> {
    private final T entry;
    private final String detail;
    private final String userAgent;
}
