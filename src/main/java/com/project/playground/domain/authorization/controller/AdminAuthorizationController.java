package com.project.playground.domain.authorization.controller;

import com.project.playground.domain.authorization.dto.request.*;
import com.project.playground.domain.authorization.dto.response.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.domain.authorization.service.RoleManagementService;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

/** 인증된 세션만 행위자로 사용하며 요청 본문에서 관리자 ID/역할을 받지 않는다. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminAuthorizationController {
    private final RoleManagementService roles;

    @GetMapping("/me")
    @RequireAdminPermission(PermissionCode.ADMIN_ACCESS)
    public ResponseEntity<AdminContextResponse> me(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body((AdminContextResponse) request.getAttribute(AdminContextResponse.ATTRIBUTE));
    }

    @GetMapping("/permissions")
    @RequireAdminPermission(PermissionCode.ROLE_READ)
    public ResponseEntity<List<PermissionResponse>> permissions(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.permissions(session.getId()));
    }

    @GetMapping("/roles")
    @RequireAdminPermission(PermissionCode.ROLE_READ)
    public ResponseEntity<RoleListResponse> list(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String cursor,
            @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.list(session.getId(), query, cursor, limit));
    }

    @GetMapping("/roles/{code}")
    @RequireAdminPermission(PermissionCode.ROLE_READ)
    public ResponseEntity<RoleDetailsResponse> get(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.get(session.getId(), code));
    }

    @PostMapping("/roles")
    @RequireAdminPermission(PermissionCode.ROLE_WRITE)
    public ResponseEntity<RoleDetailsResponse> create(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session, @Valid @RequestBody CreateRoleRequest request) {
        var result = roles.create(session.getId(), request);
        return ResponseEntity.created(URI.create("/api/admin/roles/" + result.getCode()))
                .cacheControl(CacheControl.noStore()).body(result);
    }

    @PutMapping("/roles/{code}")
    @RequireAdminPermission({PermissionCode.ROLE_WRITE, PermissionCode.ROLE_PERMISSION_UPDATE})
    public ResponseEntity<RoleDetailsResponse> update(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @Valid @RequestBody UpdateRoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.update(session.getId(), code, request));
    }

    @DeleteMapping("/roles/{code}")
    @RequireAdminPermission(PermissionCode.ROLE_WRITE)
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @PathVariable @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String code,
            @RequestParam @PositiveOrZero long expectedVersion) {
        roles.delete(session.getId(), code, expectedVersion);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @GetMapping("/users/{userId}/role")
    @RequireAdminPermission(PermissionCode.USER_ROLE_UPDATE)
    public ResponseEntity<UserRoleResponse> getUserRole(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session, @PathVariable @Positive Long userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(roles.getUserRole(session.getId(), userId));
    }

    @PutMapping("/users/{userId}/role")
    @RequireAdminPermission(PermissionCode.USER_ROLE_UPDATE)
    public ResponseEntity<UserRoleResponse> changeUserRole(
            @AuthenticationPrincipal(errorOnInvalidType = true) Session session,
            @PathVariable @Positive Long userId, @Valid @RequestBody ChangeUserRoleRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(roles.changeUserRole(session.getId(), userId, request));
    }
}

