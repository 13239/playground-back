package com.project.playground.domain.authorization.dto.response;

import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.domain.authorization.enums.PermissionCode;
import lombok.Builder;
import lombok.Getter;

import java.util.Set;

/** 한 요청에서만 재사용하는 권한 정보다. JWT·Redis 세션의 오래된 역할에 의존하지 않는다. */
@Getter
public class AdminContextResponse {
    public static final String ATTRIBUTE = AdminContextResponse.class.getName();

    private final Long userId;
    private final String roleCode;
    private final String roleName;
    private final long roleVersion;
    private final long membershipVersion;
    private final Set<PermissionCode> permissions;

    @Builder
    public AdminContextResponse(Long userId, String roleCode, String roleName, long roleVersion,
                                long membershipVersion, Set<PermissionCode> permissions) {
        this.userId = userId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleVersion = roleVersion;
        this.membershipVersion = membershipVersion;
        this.permissions = Set.copyOf(permissions);
    }

    /** 기존 응답의 최고 관리자 플래그도 현재 코드 ROOT로만 계산한다. */
    public boolean isSuperAdmin() {
        return Role.ROOT.equals(roleCode);
    }

    public boolean allows(PermissionCode permission) {
        // DB를 직접 잘못 수정해도 최고 관리자 전용 기능을 일반 역할로 우회하지 못한다.
        return permissions.contains(permission) && (!permission.isSuperAdminOnly() || isSuperAdmin());
    }
}
