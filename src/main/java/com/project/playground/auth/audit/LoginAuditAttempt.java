package com.project.playground.auth.audit;

import com.project.playground.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;

import java.sql.SQLException;
import java.util.*;

/** 입력 본문·비밀번호·해시·토큰·예외 메시지를 받을 수 없는 명시적인 진단 필드 집합이다. */
public final class LoginAuditAttempt {
    private final String id;
    private final long started = System.nanoTime();
    private final HttpServletResponse response;
    private final LinkedHashMap<String, Object> facts = new LinkedHashMap<>();
    private final List<Map<String, Object>> steps = new ArrayList<>();
    private String stage;
    private String reason;
    private ErrorCode failure;
    private long stepStarted;
    private boolean mismatch;
    private Long userId;

    public LoginAuditAttempt(String id, HttpServletResponse response) {
        this.id = id;
        this.response = response;
        step("REQUEST_RECEIVED");
    }
    public String id() { return id; }
    public Long userId() { return userId; }
    public void user(Long value) { userId = value; }
    public boolean credentialsMismatch() { return mismatch; }
    public void mismatch(boolean accountFound) {
        mismatch = true;
        reason(accountFound ? "PASSWORD_MISMATCH" : "ACCOUNT_NOT_FOUND");
        response.setHeader("X-Login-Credentials-Mismatch", "1");
    }
    public void step(String next) {
        if (!code(next) || Objects.equals(stage, next)) return;
        finishStep("SUCCESS");
        stage = next;
        stepStarted = System.nanoTime();
        reason = null;
    }
    public void reason(String value) { if (code(value)) reason = value; }
    public void failure(ErrorCode value) { if (failure == null) failure = value; }
    public ErrorCode failure() { return failure; }
    public void fact(String key, Object value) {
        if (!Set.of("username", "nickname", "keepLoggedIn", "accountFound", "accountStatus", "accountDeleted",
                "credentialsVerified", "bffVerified", "proofRequired", "replacementRequested", "redisOutcome").contains(key)) return;
        if (value instanceof Boolean) facts.put(key, value);
        else if (value instanceof String text) {
            int max = key.equals("username") || key.equals("nickname") ? 50 : 80;
            if (text.codePointCount(0, text.length()) <= max && text.codePoints().noneMatch(Character::isISOControl)) facts.put(key, text);
        }
    }
    public void exception(Throwable error) {
        if (facts.containsKey("exceptionType")) return;
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var current = error;
        facts.put("exceptionType", error.getClass().getName());
        for (int index = 0; current != null && index < 8 && seen.add(current); index++, current = current.getCause()) {
            facts.put("rootExceptionType", current.getClass().getName());
            if (current instanceof SQLException sql) {
                if (sql.getSQLState() != null && sql.getSQLState().matches("[A-Z0-9]{5}")) facts.put("sqlState", sql.getSQLState());
                facts.put("sqlErrorCode", sql.getErrorCode());
            }
        }
    }
    public Map<String, Object> finish(int status, Map<String, Object> environment) {
        boolean failed = status >= 400;
        finishStep(failed ? "FAILURE" : "SUCCESS");
        var result = new LinkedHashMap<>(facts);
        result.putAll(environment);
        result.put("schemaVersion", 1);
        result.put("attemptId", id);
        result.put("source", "BACKEND");
        result.put("stage", stage);
        result.put("reason", failed ? Objects.requireNonNullElse(reason,
                failure == null ? "UNEXPECTED_RESPONSE" : failure.name()) : "LOGIN_COMPLETED");
        result.put("httpStatus", status);
        result.put("durationMillis", Math.max(0, (System.nanoTime() - started) / 1_000_000));
        result.put("steps", List.copyOf(steps));
        if (failure != null) result.put("originalCode", failure.name());
        return Map.copyOf(result);
    }
    private void finishStep(String outcome) {
        if (stage == null || steps.size() >= 32) return;
        steps.add(Map.of("stage", stage, "outcome", outcome,
                "durationMillis", Math.max(0, (System.nanoTime() - stepStarted) / 1_000_000)));
    }
    private static boolean code(String value) { return value != null && value.matches("[A-Z][A-Z0-9_]{0,79}"); }
}
