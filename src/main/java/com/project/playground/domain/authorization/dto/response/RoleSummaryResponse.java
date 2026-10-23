package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.authorization.entity.Role;
import lombok.Builder;
import lombok.Getter;

/** 역할 목록에는 회원 수나 전체 기능 목록을 읽지 않아 역할 수가 늘어도 조회 비용을 제한한다. */
@Getter
@Builder
public class RoleSummaryResponse {
    private final String code;
    private final String name;
    private final String description;
    private final boolean systemRole;
    private final long version;

    public static RoleSummaryResponse from(Role role) {
        return RoleSummaryResponse.builder().code(role.getCode()).name(role.getName())
                .description(role.getDescription()).systemRole(role.isSystemRole()).version(role.getVersion()).build();
    }
}

