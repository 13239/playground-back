package com.project.playground.domain.adminmonitor.controller;

import com.project.playground.domain.adminmonitor.dto.*;
import com.project.playground.domain.adminmonitor.service.*;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/** 대시보드와 서비스 상태의 권한을 독립 검사한다. 상태 조작·원시 진단 경로는 제공하지 않는다. */
@RestController @RequestMapping("/api/admin") @RequiredArgsConstructor
public class AdminMonitoringController {
    private final AdminDashboardService dashboard;
    private final AdminServiceStatusService status;
    @GetMapping("/dashboard") @RequireAdminPermission(PermissionCode.DASHBOARD_READ)
    public ResponseEntity<DashboardResponse> dashboard(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @Pattern(regexp = "TODAY|WEEK|MONTH") String period) { return response(dashboard.dashboard(actor.getId(), period == null ? "TODAY" : period)); }
    @GetMapping("/service-status") @RequireAdminPermission(PermissionCode.SERVICE_STATUS_READ)
    public ResponseEntity<ServiceStatusResponse> status(@AuthenticationPrincipal Session actor) { return response(status.snapshot(actor.getId())); }
    private <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
