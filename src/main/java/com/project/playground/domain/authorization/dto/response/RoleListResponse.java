package com.project.playground.domain.authorization.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** 전체 count 없이 다음 코드 커서로 역할 목록을 이어서 탐색한다. */
@Getter
@Builder
public class RoleListResponse {
    private final List<RoleSummaryResponse> items;
    private final String nextCursor;
}

