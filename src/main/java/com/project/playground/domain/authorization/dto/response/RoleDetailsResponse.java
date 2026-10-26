package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.domain.authorization.enums.PermissionCode;
import lombok.Builder;
import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/** 선택한 역할 하나의 편집 정보이며 ROOT 코드로 편집을 막고 실제 권한은 항상 전체 집합이다. */
@Getter
@Builder
public class RoleDetailsResponse {
    private final String code;
    private final String name;
    private final String description;
    private final boolean editable;
    private final long version;
    private final Set<PermissionCode> permissions;

    public static RoleDetailsResponse from(Role role) {
        return RoleDetailsResponse.builder().code(role.getCode()).name(role.getName()).description(role.getDescription())
                .editable(!role.isRoot()).version(role.getVersion())
                .permissions(Set.copyOf(role.isRoot() ? EnumSet.allOf(PermissionCode.class) : role.getPermissions())).build();
    }
}

