package com.project.playground.auth.audit;

import com.project.playground.auth.clientip.LoginClientIpResolver;
import com.project.playground.global.exception.ApiProblemFactory;
import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.time.*;
import java.util.*;
import java.util.regex.Pattern;

/** 보안 필터·DTO 검증에서 끝난 로그인까지 한 번만 기록한다. 본문 캐시는 메모리에서만 사용한다. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 50) @RequiredArgsConstructor @Slf4j
public class LoginAuditFilter extends OncePerRequestFilter {
    private final LoginAuditOutbox outbox;
    private final LoginClientIpResolver clientIps;
    private final Environment environment;
    private final JsonMapper json;
    private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getMethod().equals("POST") || !request.getRequestURI().equals("/api/auth/login");
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var cached = new ContentCachingRequestWrapper(request, 8192);
        String supplied = request.getHeader("X-Login-Attempt-Id");
        String id = supplied != null && supplied.matches("[a-f0-9-]{36}") ? supplied : UUID.randomUUID().toString();
        var attempt = new LoginAuditAttempt(id, response);
        LocalDateTime occurredAt = LocalDateTime.now(ZONE);
        response.setHeader("X-Login-Attempt-Id", id);
        LoginAuditContext.open(attempt);
        int finalStatus = 503;
        try {
            chain.doFilter(cached, response);
            finalStatus = response.getStatus();
        } catch (Exception failure) {
            attempt.exception(failure);
            attempt.reason(failure instanceof IOException ? "REQUEST_CONNECTION_FAILED" : "UNEXPECTED_EXCEPTION");
            attempt.failure(ErrorCode.LOGIN_SERVER_ERROR);
            if (!response.isCommitted()) {
                response.resetBuffer();
                response.setStatus(503);
                response.setContentType("application/problem+json");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-store");
                try { response.getOutputStream().write(json.writeValueAsBytes(ApiProblemFactory.create(ErrorCode.LOGIN_SERVER_ERROR))); }
                catch (IOException ignored) { /* 클라이언트 연결이 끊어져도 최종 기록은 저장한다. */ }
            }
        } finally {
            try {
                // 요청 DTO가 만들어지기 전에 실패한 경우에도 읽힌 JSON에서 아이디 하나만 제한해서 추출한다.
                if (cached.getContentAsByteArray().length > 0) {
                    try {
                        var input = json.readTree(cached.getContentAsByteArray());
                        if (input.has("username") && input.get("username").isString()) attempt.fact("username", input.get("username").asString());
                    } catch (RuntimeException ignored) { /* 원문·파싱 오류 메시지는 보관하지 않는다. */ }
                }
                String ip;
                try { ip = clientIps.resolve(request); } catch (RuntimeException ignored) { ip = request.getRemoteAddr(); }
                String agent = request.getHeader("X-Client-User-Agent");
                if (agent == null) agent = request.getHeader("User-Agent");
                agent = agent == null ? null : agent.replaceAll("\\p{Cntrl}", "").substring(0, Math.min(255, agent.replaceAll("\\p{Cntrl}", "").length()));
                Map<String, Object> metadata;
                try { metadata = environment(); }
                catch (RuntimeException unavailable) { metadata = Map.of("environment", "unavailable"); }
                var detail = attempt.finish(finalStatus, metadata);
                String code = finalStatus < 400 ? null : attempt.failure() == ErrorCode.LOGIN_CONFIRMATION_REQUIRED
                        ? ErrorCode.LOGIN_CONFIRMATION_REQUIRED.name() : attempt.credentialsMismatch()
                        ? ErrorCode.INVALID_CREDENTIALS.name() : ErrorCode.LOGIN_SERVER_ERROR.name();
                outbox.enqueue(new LoginAuditRecord(UUID.randomUUID().toString(), occurredAt, finalStatus < 400,
                        code, attempt.userId(), ip, agent, detail));
            } catch (RuntimeException auditFailure) {
                log.error("LOGIN_AUDIT_CAPTURE_FAILED attemptId={}", id);
            } finally { LoginAuditContext.close(); }
        }
    }
    private Map<String, Object> environment() {
        var result = new LinkedHashMap<String, Object>();
        result.put("environment", String.join(",", environment.getActiveProfiles()));
        result.put("release", safe(environment.getProperty("APP_RELEASE_TAG", "local")));
        result.put("instance", safe(System.getenv().getOrDefault("HOSTNAME", "local")));
        result.put("javaVersion", safe(System.getProperty("java.version")));
        String jdbc = environment.getProperty("spring.datasource.url", "");
        var match = Pattern.compile("^jdbc:mysql://([A-Za-z0-9.\\[\\]:_-]{1,160})/([A-Za-z0-9_-]{1,64})(?:\\?.*)?$").matcher(jdbc);
        result.put("database", match.matches() ? match.group(1) + "/" + match.group(2) : "unavailable");
        result.put("redis", safe(environment.getProperty("spring.data.redis.host", "unknown")) + ":"
                + safe(environment.getProperty("spring.data.redis.port", "6379")) + "/"
                + safe(environment.getProperty("spring.data.redis.database", "0")));
        result.put("proofRequired", environment.getProperty("auth.proof.required", Boolean.class, true));
        return result;
    }
    private static String safe(String value) {
        if (value == null || value.length() > 180 || value.codePoints().anyMatch(Character::isISOControl)) return "unavailable";
        return value;
    }
}
