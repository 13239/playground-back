package com.project.playground.domain.usermanagement.service;

import com.project.playground.auth.repository.RedisSessionRepository;
import com.project.playground.domain.adminaudit.service.AdminAuditService;
import com.project.playground.domain.authorization.dto.response.AdminContextResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.repository.RoleRepository;
import com.project.playground.domain.authorization.service.AdminAuthorizationService;
import com.project.playground.domain.user.entity.User;
import com.project.playground.domain.user.enums.UserStatus;
import com.project.playground.domain.user.repository.*;
import com.project.playground.domain.user.service.UserService;
import com.project.playground.domain.usermanagement.dto.*;
import com.project.playground.domain.usermanagement.repository.AdminUserQueryRepository;
import com.project.playground.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** 현재 DB 권한과 동일한 행 잠금·버전·동기 감사 기록을 사용한다. 관리자 닉네임 수정/삭제/탈퇴 복구는 제공하지 않는다. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class AdminUserService {
    private final AdminAuthorizationService authorization;
    private final RoleRepository roles;
    private final UserRepository users;
    private final UserNicknameHistoryRepository history;
    private final AdminUserQueryRepository queries;
    private final UserService accounts;
    private final RedisSessionRepository sessions;
    private final AdminAuditService audit;

    public AdminUserPage list(Long actorId, String query, String status, String role, int page, int size) {
        authorization.require(actorId, PermissionCode.USER_READ);
        validatePage(page, size);
        return queries.list(query, status, role, page, size);
    }
    public AdminUserDetails details(Long actorId, Long targetId) {
        authorization.require(actorId, PermissionCode.USER_READ);
        return queries.details(targetId);
    }
    public UserRoleOptions roleOptions(Long actorId, String query, String cursor, int limit) {
        authorization.require(actorId, PermissionCode.USER_READ);
        String search = query == null || query.isBlank() ? null : "%" + query.strip().toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        var pageable = PageRequest.of(0, Math.clamp(limit, 1, 100));
        var page = search == null || query.strip().chars().allMatch(c -> c <= 0x7f) ? roles.search(search, cursor, pageable) : roles.searchName(search, cursor, pageable);
        var items = page.getContent().stream().map(r -> UserRoleOptions.Option.builder().code(r.getCode()).name(r.getName()).build()).toList();
        return UserRoleOptions.builder().items(items).nextCursor(page.hasNext() ? items.getLast().getCode() : null).build();
    }
    public NicknameHistoryResponse history(Long actorId, Long targetId, int page, int size) {
        authorization.require(actorId, PermissionCode.USER_READ);
        // 이력 화면의 전용 표시 개수만 허용하고 회원 목록의 기존 제한은 유지한다.
        if (page < 1 || !Set.of(10, 15, 20, 30).contains(size)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        if (!users.existsByIdAndDeletedAtIsNull(targetId)) throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        long total = history.countByUserId(targetId);
        int totalPages = totalPages(total, size);
        // 마지막 페이지의 이력이 줄어도 유효한 마지막 페이지로 보정한다.
        int actualPage = Math.min(page, totalPages);
        var rows = history.findByUserIdOrderByIdDesc(targetId, PageRequest.of(actualPage - 1, size));
        var items = rows.stream().map(NicknameHistoryResponse.Item::from).toList();
        return NicknameHistoryResponse.builder().items(items).page(actualPage).size(size).totalPages(totalPages).totalElements(total).build();
    }
    @Transactional
    public AdminUserSensitive sensitive(Long actorId, Long targetId) {
        var actor = requireWrite(actorId, PermissionCode.USER_SENSITIVE_READ);
        var response = queries.sensitive(targetId);
        // 감사에는 열람 사실/필드/버전만 남기고 별도 사유나 개인정보 원문을 복제하지 않는다.
        audit.write(actor, "USER_SENSITIVE_VIEWED", "USER", targetId.toString(), null,
                Map.of("fields", List.of("email", "phone", "birthday", "gender"), "version", response.getManagementVersion()));
        return response;
    }
    @Transactional
    public AdminUserDetails status(Long actorId, Long targetId, AdminUserStatusRequest request) {
        var actor = requireWrite(actorId, PermissionCode.USER_STATUS_UPDATE);
        var user = locked(targetId);
        requireVersion(user.getManagementVersion(), request.getExpectedVersion());
        user.assertNotSuperAdmin();
        // 본인의 비활성화·탈퇴는 세션을 차단하기 전에 거절한다.
        if (actorId.equals(targetId) && !request.getStatus().equals("ACTIVE")) throw new BusinessException(ErrorCode.USER_SELF_OPERATION_NOT_ALLOWED);
        String before = user.getStatus().name();
        // 기존 재활성화의 커밋 이후 조건부 Redis 해제와 실패 시 차단 유지 정책을 그대로 재사용한다.
        switch (request.getStatus()) {
            case "ACTIVE" -> accounts.activate(targetId);
            case "INACTIVE" -> accounts.deactivate(targetId);
            case "WITHDRAWN" -> accounts.withdraw(targetId);
            default -> throw new BusinessException(ErrorCode.VALIDATION_FAILED);
        }
        users.flush();
        audit.write(actor, "USER_STATUS_CHANGED", "USER", targetId.toString(), request.getReason(),
                Map.of("before", before, "after", user.getStatus().name(), "version", user.getManagementVersion()));
        return queries.details(targetId);
    }
    @Transactional
    public void revokeSessions(Long actorId, Long targetId, AdminUserActionRequest request) {
        var actor = requireWrite(actorId, PermissionCode.USER_SESSION_REVOKE);
        var user = locked(targetId);
        requireVersion(user.getManagementVersion(), request.getExpectedVersion());
        // 일반 관리자가 최고 관리자의 세션을 종료하지 못하게 하고, ROOT 본인의 종료는 허용한다.
        if (!actor.isSuperAdmin()) user.assertNotSuperAdmin();
        sessions.revokeAll(targetId);
        audit.write(actor, "USER_SESSIONS_REVOKED", "USER", targetId.toString(), request.getReason(), Map.of("version", user.getManagementVersion()));
    }
    private AdminContextResponse requireWrite(Long actorId, PermissionCode permission) {
        roles.lockAuthorizationPolicy().orElseThrow(() -> new BusinessException(ErrorCode.AUTHORIZATION_SERVICE_UNAVAILABLE));
        users.findForUpdateById(actorId).orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
        authorization.require(actorId, PermissionCode.USER_READ);
        return authorization.require(actorId, permission);
    }
    // 탈퇴·삭제 회원의 관리 변경을 거절해 일반 상태 변경으로 복구하지 않는다.
    private User locked(Long id) { return users.findForUpdateById(id).filter(u -> u.getDeletedAt() == null && u.getStatus() != UserStatus.WITHDRAWN).orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND)); }
    private static void requireVersion(long actual, long expected) { if (actual != expected) throw new BusinessException(ErrorCode.USER_MANAGEMENT_CHANGED); }
    /** 화면에서 지원하는 페이지 크기만 허용해 임의의 대량 조회를 방지한다. */
    private static void validatePage(int page, int size) {
        if (page < 1 || !Set.of(10, 20, 30, 50).contains(size)) throw new BusinessException(ErrorCode.VALIDATION_FAILED);
    }
    private static int totalPages(long total, int size) { return Math.max(1, Math.toIntExact(Math.ceilDiv(total, size))); }
}
