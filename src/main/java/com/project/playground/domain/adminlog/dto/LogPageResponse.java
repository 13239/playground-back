package com.project.playground.domain.adminlog.dto;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 집계는 번호 이동에만 사용하며 로그 목록에 원문 JSON이나 인증 정보를 포함하지 않는다. */
@Getter @Builder
public class LogPageResponse<T> {
    private final List<T> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
}
