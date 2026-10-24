package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;

/** 변경 전·후는 현재 엔티티를 재구성하지 않고 감사에 저장된 스냅샷에서 읽는다. */
@Getter @Builder
public class LogChange {
    private final String label;
    private final String before;
    private final String after;
}
