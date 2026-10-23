package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.authorization.enums.PermissionCode;
import lombok.Builder;
import lombok.Getter;

/** 기능 코드는 구현에 연결된 사전이며 화면에서는 이 목록을 역할별로 배정한다. */
@Getter
@Builder
public class PermissionResponse {
    private final PermissionCode code;
    private final String name;
    private final String category;
    private final boolean superAdminOnly;

    public static PermissionResponse from(PermissionCode code) {
        return PermissionResponse.builder().code(code).name(code.getDisplayName())
                .category(code.getCategory()).superAdminOnly(code.isSuperAdminOnly()).build();
    }
}

