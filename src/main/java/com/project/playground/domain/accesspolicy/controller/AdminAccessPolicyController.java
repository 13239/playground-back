package com.project.playground.domain.accesspolicy.controller;

import com.project.playground.domain.accesspolicy.dto.*;
import com.project.playground.domain.accesspolicy.service.AccessPolicyService;
import com.project.playground.domain.authorization.dto.response.RolePageResponse;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 설정 스위치와 ROOT 전용 예외 역할 변경을 별도 명령으로 제공한다. */
@RestController @RequestMapping("/api/admin/access-policy") @RequiredArgsConstructor
public class AdminAccessPolicyController {
    private final AccessPolicyService policy;

    @GetMapping @RequireAdminPermission(PermissionCode.ACCESS_POLICY_READ)
    public ResponseEntity<AccessPolicyResponse> get(@AuthenticationPrincipal Session session) {
        return response(policy.get(session.getId()));
    }
    /** 허용 권한의 번호 조회도 최상위 관리자 전용 편집 권한을 재확인한다. */
    @GetMapping("/roles") @RequireAdminPermission(PermissionCode.ACCESS_EXEMPTION_UPDATE)
    public ResponseEntity<RolePageResponse> roles(@AuthenticationPrincipal Session session,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(policy.roleOptions(session.getId(), query, page, size));
    }
    @PutMapping @RequireAdminPermission(PermissionCode.ACCESS_POLICY_UPDATE)
    public ResponseEntity<AccessPolicyResponse> update(@AuthenticationPrincipal Session session, @Valid @RequestBody UpdateAccessPolicyRequest request) {
        return response(policy.update(session.getId(), request));
    }
    @PutMapping("/exemptions") @RequireAdminPermission(PermissionCode.ACCESS_EXEMPTION_UPDATE)
    public ResponseEntity<AccessPolicyResponse> exemptions(@AuthenticationPrincipal Session session, @Valid @RequestBody UpdateAccessExemptionsRequest request) {
        return response(policy.exemptions(session.getId(), request));
    }
    private ResponseEntity<AccessPolicyResponse> response(AccessPolicyResponse result) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
