package com.project.playground.domain.authorization.service;

import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.accesspolicy.repository.AccessPolicyRepository;
import com.project.playground.domain.authorization.dto.request.*;
import com.project.playground.domain.authorization.dto.response.*;
import com.project.playground.domain.authorization.entity.Role;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.user.repository.UserRepository;
import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** 역할 편집·회원 배정은 서버의 현재 권한, 변경 버전, 동기 감사 기록을 한 트랜잭션으로 처리한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleManagementService {
    private final AdminAuthorizationService authorization;
    private final RoleRepository roles;
    private final UserRepository users;
    private final AdminAuditService audit;
    private final RedisSessionRepository sessions;
    private final AccessPolicyRepository accessPolicies;

    public RoleListResponse list(Long actorId, String query, String cursor, int limit) {
        authorization.require(actorId, PermissionCode.ROLE_READ);
        // SQL LIKE 와일드카드를 일반 검색 문자로 취급하며 요청자가 전체 페이지 크기를 늘리지 못하게 한다.
        String search = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        // 한글 검색어는 UTF-8 이름에만 비교한다. 코드 커서는 두 조회에서 동일하게 유지한다.
        var pageable = PageRequest.of(0, Math.clamp(limit, 1, 100));
        var page = search == null || query.strip().chars().allMatch(value -> value <= 0x7f)
                ? roles.search(search, cursor, pageable) : roles.searchName(search, cursor, pageable);
        var items = page.getContent().stream().map(RoleSummaryResponse::from).toList();
        return RoleListResponse.builder().items(items)
                .nextCursor(page.hasNext() ? items.getLast().getCode() : null).build();
    }

    public RoleDetailsResponse get(Long actorId, String code) {
        authorization.require(actorId, PermissionCode.ROLE_READ);
        return RoleDetailsResponse.from(findRole(code));
    }

    public List<PermissionResponse> permissions(Long actorId) {
        authorization.require(actorId, PermissionCode.ROLE_READ);
        return Arrays.stream(PermissionCode.values()).map(PermissionResponse::from).toList();
    }

    /** 역할 배정에 필요한 현재 버전을 수동 DB 조회 없이 관리자 API로 확인할 수 있게 한다. */
    public UserRoleResponse getUserRole(Long actorId, Long userId) {
        authorization.require(actorId, PermissionCode.USER_ROLE_UPDATE);
        var membership = users.findRoleForManagement(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return UserRoleResponse.builder().userId(membership.getUserId()).roleCode(membership.getRoleCode())
                .version(membership.getRoleVersion()).build();
    }

    @Transactional
    public RoleDetailsResponse create(Long actorId, CreateRoleRequest request) {
        var actor = authorization.requireSuperAdminForUpdate(actorId);
        if (roles.existsById(request.getCode())) throw new BusinessException(ErrorCode.ROLE_ALREADY_EXISTS);
        var role = Role.builder().code(request.getCode()).name(request.getName().strip())
                .description(request.getDescription()).permissions(request.getPermissions()).build();
        var saved = roles.saveAndFlush(role);
        audit.write(actor, "ROLE_CREATED", "ROLE", saved.getCode(), request.getReason(), Map.of("after", snapshot(saved)));
        return RoleDetailsResponse.from(saved);
    }

    @Transactional
    public RoleDetailsResponse update(Long actorId, String code, UpdateRoleRequest request) {
        var actor = authorization.requireSuperAdminForUpdate(actorId);
        var role = findRole(code);
        requireVersion(role.getVersion(), request.getExpectedVersion());
        var before = snapshot(role);
        role.update(request.getName().strip(), request.getDescription(), request.getPermissions());
        // 기능 컬렉션 변경의 버전도 flush 이후 응답하므로 다음 편집에 이전 버전을 재사용하지 않는다.
        roles.flush();
        audit.write(actor, "ROLE_UPDATED", "ROLE", code, request.getReason(),
                Map.of("before", before, "after", snapshot(role)));
        return RoleDetailsResponse.from(role);
    }

    @Transactional
    public void delete(Long actorId, String code, long expectedVersion) {
        var actor = authorization.requireSuperAdminForUpdate(actorId);
        var role = findRole(code);
        requireVersion(role.getVersion(), expectedVersion);
        role.assertDeletable();
        if (users.existsByRoleCode(code)) throw new BusinessException(ErrorCode.ROLE_IN_USE);
        // 접속 허용 중인 역할은 정책에서 먼저 제외해야 한다. 과거 폐기 이력은 삭제 후에도 보존한다.
        if (accessPolicies.findById(1L).orElseThrow().getExemptRoleCodes().contains(code)) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        var before = snapshot(role);
        roles.delete(role);
        roles.flush();
        audit.write(actor, "ROLE_DELETED", "ROLE", code, null, Map.of("before", before));
    }

    @Transactional
    public UserRoleResponse changeUserRole(Long actorId, Long userId, ChangeUserRoleRequest request) {
        var actor = authorization.requireSuperAdminForUpdate(actorId);
        var user = users.findForUpdateById(userId).filter(value -> value.getDeletedAt() == null)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        requireVersion(user.getRoleVersion(), request.getExpectedVersion());
        findRole(request.getRoleCode());
        String before = user.getRoleCode();
        // 최고 관리자 보호는 같은 역할로의 요청에도 먼저 적용한다.
        user.assertNotSuperAdmin();
        if (Role.SUPER_ADMIN.equals(request.getRoleCode())) throw new BusinessException(ErrorCode.ROLE_PROTECTED);
        if (before.equals(request.getRoleCode())) return UserRoleResponse.from(user);
        // 역할에 결합된 기존 세션을 먼저 종료한다. 실패하면 DB 역할도 변경하지 않는다.
        sessions.revokeAll(userId);
        user.changeRole(request.getRoleCode());
        users.flush();
        audit.write(actor, "USER_ROLE_CHANGED", "USER", userId.toString(), request.getReason(),
                Map.of("before", before, "after", user.getRoleCode(), "version", user.getRoleVersion()));
        return UserRoleResponse.from(user);
    }

    private Role findRole(String code) {
        return roles.findWithPermissionsByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
    }

    private static void requireVersion(long actual, long expected) {
        if (actual != expected) throw new BusinessException(ErrorCode.AUTHORIZATION_CHANGED);
    }

    private static Map<String, Object> snapshot(Role role) {
        var result = new LinkedHashMap<String, Object>();
        result.put("code", role.getCode());
        result.put("name", role.getName());
        result.put("description", role.getDescription());
        result.put("permissions", role.getPermissions().stream().map(Enum::name).sorted().toList());
        result.put("version", role.getVersion());
        return result;
    }
}

