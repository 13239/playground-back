package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.authorization.entity.Role;
import lombok.Builder;
import lombok.Getter;

/** 역할 목록에는 삭제 보호 플래그·회원 수·전체 기능 목록을 넣지 않아 조회 비용을 제한한다. */
@Getter
@Builder
public class RoleSummaryResponse {
    private final String code;
    private final String name;
    private final String description;
    private final long version;

    public static RoleSummaryResponse from(Role role) {
        return RoleSummaryResponse.builder().code(role.getCode()).name(role.getName())
                .description(role.getDescription()).version(role.getVersion()).build();
    }
}

