package com.project.playground.auth.audit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.extern.jackson.Jacksonized;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.*;
import java.util.*;

/** 기존 BFF의 mTLS·전용 키를 통과하는 내부 기록 전용 경로다. 브라우저에 직접 공개하지 않는다. */
@RestController @RequiredArgsConstructor
public class LoginAuditReportController {
    private final LoginAuditOutbox outbox;

    @PostMapping("/internal/auth/login-failures")
    public ResponseEntity<Void> report(@Valid @RequestBody Report report, HttpServletRequest request) {
        if (request.getHeader("Origin") != null || !Set.of("BFF", "BROWSER").contains(report.source)
                || !report.stage.startsWith(report.source + "_")) throw new BusinessException(ErrorCode.INVALID_REQUEST_BODY);
        Instant receivedAt = Instant.now();
        var detail = new LinkedHashMap<String, Object>();
        detail.put("schemaVersion", 1); detail.put("attemptId", report.attemptId);
        detail.put("source", report.source); detail.put("stage", report.stage); detail.put("reason", report.reason);
        detail.put("durationMillis", report.durationMillis); detail.put("httpStatus", report.httpStatus);
        detail.put("environment", report.environment); detail.put("release", report.release);
        // 서버 시각 차이 자체가 인증 장애 원인일 수 있으므로 시각이 다른 보고를 버리지 않는다.
        detail.put("reportedAt", report.occurredAt.toString());
        long difference;
        try { difference = Duration.between(report.occurredAt, receivedAt).toMillis(); }
        catch (ArithmeticException ignored) { difference = report.occurredAt.isBefore(receivedAt) ? Long.MAX_VALUE : Long.MIN_VALUE; }
        detail.put("deliveryDelayMillis", difference);
        if (report.username != null && report.username.codePoints().noneMatch(Character::isISOControl)) detail.put("username", report.username);
        if (report.exceptionType != null) detail.put("exceptionType", report.exceptionType);
        // 브라우저가 주장한 상태는 인증된 본인 정보로 취급하지 않는다.
        detail.put("clientReported", report.source.equals("BROWSER"));
        outbox.enqueue(new LoginAuditRecord(report.eventId, LocalDateTime.ofInstant(receivedAt, ZoneId.of("Asia/Seoul")),
                false, ErrorCode.LOGIN_SERVER_ERROR.name(), null, report.clientIp, report.userAgent, Map.copyOf(detail)));
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    @Getter @Builder @Jacksonized
    public static class Report {
        @NotNull @Pattern(regexp="[a-f0-9-]{36}") private final String eventId;
        @NotNull @Pattern(regexp="[a-f0-9-]{36}") private final String attemptId;
        @NotNull private final Instant occurredAt;
        @NotNull @Pattern(regexp="BFF|BROWSER") private final String source;
        @NotNull @Pattern(regexp="[A-Z][A-Z0-9_]{0,79}") private final String stage;
        @NotNull @Pattern(regexp="[A-Z][A-Z0-9_]{0,79}") private final String reason;
        @Min(0) @Max(86400000) private final long durationMillis;
        @Min(0) @Max(599) private final int httpStatus;
        @NotNull @Pattern(regexp="local|dev|prod|unknown") private final String environment;
        @NotNull @Pattern(regexp="[A-Za-z0-9_.-]{1,128}") private final String release;
        @Size(max=50) private final String username;
        @Pattern(regexp="[0-9a-fA-F:.]{1,45}") private final String clientIp;
        @Size(max=255) private final String userAgent;
        @Pattern(regexp="[A-Za-z0-9_.$]{1,120}") private final String exceptionType;
    }
}
