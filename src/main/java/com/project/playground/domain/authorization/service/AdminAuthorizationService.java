package com.project.playground.domain.authorization.service;

import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.stream.Collectors;

/** Spring Security의 역할 기능과 분리된 관리자 권한 판단의 단일 진입점이다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminAuthorizationService {
    private final UserRepository users;
    private final RoleRepository roles;

    public AdminContextResponse require(Long userId, PermissionCode permission) {
        var membership = users.findActiveRole(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        var role = roles.findWithPermissionsByCode(membership.getRoleCode())
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        var context = AdminContextResponse.builder()
                .userId(userId).roleCode(role.getCode()).roleName(role.getName())
                .roleVersion(role.getVersion()).membershipVersion(membership.getRoleVersion())
                // ROOT는 삭제 보호와 같은 코드 판별로 전체 기능을 부여하며 DB 플래그를 읽지 않는다.
                .permissions(role.isRoot() ? EnumSet.allOf(PermissionCode.class)
                        : role.getPermissions().stream().filter(value -> !value.isSuperAdminOnly()).collect(Collectors.toUnmodifiableSet()))
                .build();
        if (!context.allows(PermissionCode.ADMIN_ACCESS) || !context.allows(permission)
                || permission.isSuperAdminOnly() && !context.isSuperAdmin()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return context;
    }

    /** 잠금 후 새로 확인하므로 HTTP 검사와 실제 변경 사이의 권한 변경도 반영한다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public AdminContextResponse requireSuperAdminForUpdate(Long actorId) {
        roles.lockAuthorizationPolicy()
                .orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        // 계정 상태 변경과도 순서를 맞추며 호출자는 반드시 쓰기 트랜잭션이어야 한다.
        users.findForUpdateById(actorId).orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        var context = require(actorId, PermissionCode.ROLE_WRITE);
        if (!context.isSuperAdmin()) throw new BusinessException(ErrorCode.ACCESS_DENIED);
        return context;
    }
}
