package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

/** 역할 배정 응답에는 개인정보나 인증 자격 증명을 포함하지 않는다. */
@Getter
@Builder
public class UserRoleResponse {
    private final Long userId;
    private final String roleCode;
    private final long version;

    public static UserRoleResponse from(User user) {
        return UserRoleResponse.builder().userId(user.getId()).roleCode(user.getRoleCode())
                .version(user.getRoleVersion()).build();
    }
}

