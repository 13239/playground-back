package com.project.playground.domain.adminlog.controller;

import com.project.playground.domain.adminlog.dto.*;
import com.project.playground.domain.adminlog.service.AdminLogService;
import com.project.playground.domain.adminlog.support.LogSearch;
import com.project.playground.domain.authorization.enums.PermissionCode;
import com.project.playground.global.security.admin.RequireAdminPermission;
import com.project.playground.global.security.session.Session;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;

/** 로그 원문 변경 경로 없이 종류별 목록·필터·선택 기록 조회만 명시적으로 공개한다. */
@RestController @RequestMapping("/api/admin/logs") @RequiredArgsConstructor
public class AdminLogController {
    private final AdminLogService logs;
    @GetMapping("/activities/options") @RequireAdminPermission(PermissionCode.ACTIVITY_LOG_READ)
    public ResponseEntity<LogOptions> activityOptions(@AuthenticationPrincipal Session actor) { return response(logs.activityOptions(actor.getId())); }
    @GetMapping("/audits/options") @RequireAdminPermission(PermissionCode.ADMIN_AUDIT_READ)
    public ResponseEntity<LogOptions> auditOptions(@AuthenticationPrincipal Session actor) { return response(logs.auditOptions(actor.getId())); }
    @GetMapping("/activities") @RequireAdminPermission(PermissionCode.ACTIVITY_LOG_READ)
    public ResponseEntity<LogPageResponse<ActivityLogSummary>> activities(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,39}") String type,
            @RequestParam(required = false) @Pattern(regexp = "SUCCESS|FAILURE") String result,
            @RequestParam(defaultValue = "1") @Positive int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return response(logs.activities(actor.getId(), LogSearch.of(from, to, query, type, result, null, page, size)));
    }
    @GetMapping("/audits") @RequireAdminPermission(PermissionCode.ADMIN_AUDIT_READ)
    public ResponseEntity<LogPageResponse<AuditLogSummary>> audits(@AuthenticationPrincipal Session actor,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) @Size(max = 100) String query,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,63}") String action,
            @RequestParam(required = false) @Pattern(regexp = "[A-Z][A-Z0-9_]{0,31}") String targetType,
            @RequestParam(defaultValue = "1") @Positive int page,
            @RequestParam(defaultValue = "10") @Min(10) @Max(50) int size) {
        return response(logs.audits(actor.getId(), LogSearch.of(from, to, query, action, null, targetType, page, size)));
    }
    @GetMapping("/activities/{id}") @RequireAdminPermission(PermissionCode.ACTIVITY_LOG_READ)
    public ResponseEntity<ActivityLogDetails> activity(@AuthenticationPrincipal Session actor, @PathVariable @Positive long id) { return response(logs.activity(actor.getId(), id)); }
    @GetMapping("/audits/{id}") @RequireAdminPermission(PermissionCode.ADMIN_AUDIT_READ)
    public ResponseEntity<AuditLogDetails> audit(@AuthenticationPrincipal Session actor, @PathVariable @Positive long id) { return response(logs.audit(actor.getId(), id)); }
    private static <T> ResponseEntity<T> response(T value) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value); }
}
