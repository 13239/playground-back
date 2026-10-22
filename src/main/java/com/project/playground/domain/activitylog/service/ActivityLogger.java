package com.project.playground.domain.activitylog.service;

import com.project.playground.auth.clientip.LoginClientIpResolver;
import com.project.playground.domain.activitylog.enums.ActivityResult;
import com.project.playground.domain.activitylog.enums.ActivityType;
import com.project.playground.domain.activitylog.event.ActivityLogEvent;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 서비스가 활동 로그를 남기는 유일한 진입점이다.
 * 요청 IP·User-Agent·발생 시각은 요청 스레드에서 확정하고, 저장 시점과 저장은 리스너에 맡긴다.
 */
@Component
@RequiredArgsConstructor
public class ActivityLogger {

    // BFF가 브라우저의 User-Agent만 담아 보내는 전용 헤더. BFF 서버 자신의 User-Agent와 구분한다.
    public static final String USER_AGENT_HEADER = "X-Client-User-Agent";

    private static final int USER_AGENT_MAX_LENGTH = 255;

    private static final Pattern CONTROL_CHARACTERS = Pattern.compile("\\p{Cntrl}");

    // 회원 테이블의 생성 시각과 같은 한국 시간으로 저장한다. 서버 JVM 기본 시간대에 의존하지 않도록 명시한다.
    private static final ZoneId OCCURRED_AT_ZONE = ZoneId.of("Asia/Seoul");

    private final ApplicationEventPublisher eventPublisher;

    private final LoginClientIpResolver clientIpResolver;

    public void success(ActivityType type, @Nullable Long userId) {
        success(type, userId, Map.of());
    }

    // detail에는 관리자 화면에 필요한 값만 넣는다. 요청 DTO·비밀번호·토큰은 넣지 않는다.
    public void success(ActivityType type, @Nullable Long userId, Map<String, ?> detail) {
        publish(type, ActivityResult.SUCCESS, userId, null, detail);
    }

    public void failure(ActivityType type, @Nullable Long userId, ErrorCode failureCode) {
        failure(type, userId, failureCode, Map.of());
    }

    // 실패 원인은 클라이언트 응답과 같은 ErrorCode 이름으로 남긴다. detail 작성 기준은 성공 로그와 같다.
    public void failure(ActivityType type, @Nullable Long userId, ErrorCode failureCode, Map<String, ?> detail) {
        publish(type, ActivityResult.FAILURE, userId, failureCode.name(), detail);
    }

    private void publish(ActivityType type, ActivityResult result, @Nullable Long userId,
                         @Nullable String failureCode, Map<String, ?> detail) {
        HttpServletRequest request = currentRequest();

        eventPublisher.publishEvent(ActivityLogEvent.builder()
                .type(type)
                .result(result)
                .failureCode(failureCode)
                .userId(userId)
                .clientIp(resolveClientIp(request))
                .userAgent(resolveUserAgent(request))
                .detail(Map.copyOf(detail))
                .occurredAt(LocalDateTime.now(OCCURRED_AT_ZONE))
                .build());
    }

    // 스케줄러처럼 HTTP 요청 밖에서 발생한 활동은 요청 정보 없이 기록한다.
    private @Nullable HttpServletRequest currentRequest() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
    }

    // 로그인 제한과 같은 신뢰 프록시 판별을 사용한다. BFF가 IP를 전달하지 않는 요청도 활동 자체는 기록한다.
    private @Nullable String resolveClientIp(@Nullable HttpServletRequest request) {
        if (request == null) return null;

        try {
            return clientIpResolver.resolve(request);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // 외부 입력이므로 제어 문자를 제거하고 컬럼 길이로 자른다. 관리자 화면에 출력할 때도 이스케이프해야 한다.
    private @Nullable String resolveUserAgent(@Nullable HttpServletRequest request) {
        if (request == null) return null;

        String userAgent = request.getHeader(USER_AGENT_HEADER);
        if (userAgent == null) return null;

        String sanitized = CONTROL_CHARACTERS.matcher(userAgent).replaceAll("").strip();
        if (sanitized.isEmpty()) return null;

        return sanitized.length() > USER_AGENT_MAX_LENGTH ? sanitized.substring(0, USER_AGENT_MAX_LENGTH) : sanitized;
    }
}
