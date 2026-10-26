package com.project.playground.domain.authorization.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 권한 관리 그리드의 1부터 시작하는 페이지 번호와 집계 결과를 반환한다. */
@Getter
@Builder
public class RolePageResponse {
    private final List<RoleSummaryResponse> items;
    private final int page;
    private final int size;
    private final int totalPages;
    private final long totalElements;
}
