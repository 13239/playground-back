package com.project.playground.domain.accesspolicy.controller;

import com.project.playground.domain.accesspolicy.dto.*;
import com.project.playground.domain.accesspolicy.service.AccessPolicyService;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 설정 스위치와 슈퍼 관리자 전용 예외 역할 변경을 별도 명령으로 제공한다. */
@RestController @RequestMapping("/api/admin/access-policy") @RequiredArgsConstructor
public class AdminAccessPolicyController {
    private final AccessPolicyService policy;

    @GetMapping @RequireAdminPermission(PermissionCode.ACCESS_POLICY_READ)
    public ResponseEntity<AccessPolicyResponse> get(@AuthenticationPrincipal Session session) {
        return response(policy.get(session.getId()));
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
