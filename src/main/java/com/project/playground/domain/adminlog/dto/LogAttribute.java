package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;

/** 허용 목록을 통과한 부가 정보만 읽기 전용 텍스트로 표시한다. */
@Getter @Builder
public class LogAttribute {
    private final String label;
    private final String value;
}
