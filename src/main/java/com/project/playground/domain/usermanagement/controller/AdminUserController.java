package com.project.playground.domain.usermanagement.controller;

import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.usermanagement.dto.*;
import com.project.playground.domain.usermanagement.service.AdminUserService;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 인증 주체만 행위자로 사용하고 읽기/민감 열람/상태/세션 종료의 기능을 각각 검사한다. */
@RestController @RequestMapping("/api/admin/users") @RequiredArgsConstructor
public class AdminUserController {
    private final AdminUserService users;
    @GetMapping("/role-options") @RequireAdminPermission(PermissionCode.USER_READ)
    public ResponseEntity<UserRoleOptions> roleOptions(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) { return response(users.roleOptions(actor.getId(), query, cursor, limit)); }
    @GetMapping @RequireAdminPermission(PermissionCode.USER_READ)
    public ResponseEntity<AdminUserPage> list(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "ACTIVE|INACTIVE|PENDING|WITHDRAWN") String status,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String roleCode,
            @RequestParam(defaultValue = "1") @Positive int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return response(users.list(actor.getId(), query, status, roleCode, page, size));
    }
    @GetMapping("/{userId}") @RequireAdminPermission(PermissionCode.USER_READ)
    public ResponseEntity<AdminUserDetails> get(@AuthenticationPrincipal Session actor, @PathVariable @Positive Long userId) { return response(users.details(actor.getId(), userId)); }
    @GetMapping("/{userId}/nickname-history") @RequireAdminPermission(PermissionCode.USER_READ)
    public ResponseEntity<NicknameHistoryResponse> history(@AuthenticationPrincipal Session actor, @PathVariable @Positive Long userId,
            @RequestParam(defaultValue = "1") @Positive int page,
            // 이력은 10개를 기본으로 최대 30개까지만 조회하며 세부 허용값은 서비스에서 검사한다.
            @RequestParam(defaultValue = "10") @Min(10) @Max(30) int size) { return response(users.history(actor.getId(), userId, page, size)); }
    // 별도 사유 입력 없이 원문을 조회하되 기존 개인정보 기능 권한과 열람 감사는 유지한다.
    @GetMapping("/{userId}/sensitive") @RequireAdminPermission({PermissionCode.USER_READ, PermissionCode.USER_SENSITIVE_READ})
    public ResponseEntity<AdminUserSensitive> sensitive(@AuthenticationPrincipal Session actor, @PathVariable @Positive Long userId) { return response(users.sensitive(actor.getId(), userId)); }
    @PutMapping("/{userId}/status") @RequireAdminPermission({PermissionCode.USER_READ, PermissionCode.USER_STATUS_UPDATE})
    public ResponseEntity<AdminUserDetails> status(@AuthenticationPrincipal Session actor, @PathVariable @Positive Long userId, @Valid @RequestBody AdminUserStatusRequest request) { return response(users.status(actor.getId(), userId, request)); }
    @PostMapping("/{userId}/sessions/revoke") @RequireAdminPermission({PermissionCode.USER_READ, PermissionCode.USER_SESSION_REVOKE})
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Session actor, @PathVariable @Positive Long userId, @Valid @RequestBody AdminUserActionRequest request) {
        users.revokeSessions(actor.getId(), userId, request); return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    private static <T> ResponseEntity<T> response(T body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
}
