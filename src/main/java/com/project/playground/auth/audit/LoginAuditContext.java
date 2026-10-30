package com.project.playground.auth.audit;

import com.project.playground.global.exception.ErrorCode;

/** 로그인 요청 스레드에서만 유지한다. 필터 finally에서 반드시 제거하며 인증 판단에는 사용하지 않는다. */
public final class LoginAuditContext {
    private static final ThreadLocal<LoginAuditAttempt> CURRENT = new ThreadLocal<>();
    private LoginAuditContext() { }
    static void open(LoginAuditAttempt attempt) { CURRENT.set(attempt); }
    static void close() { CURRENT.remove(); }
    public static LoginAuditAttempt current() { return CURRENT.get(); }
    public static void step(String stage) { var attempt = current(); if (attempt != null) attempt.step(stage); }
    public static void reason(String reason) { var attempt = current(); if (attempt != null) attempt.reason(reason); }
    public static void exception(Throwable error) { var attempt = current(); if (attempt != null) attempt.exception(error); }
    public static void fact(String key, Object value) { var attempt = current(); if (attempt != null) attempt.fact(key, value); }

    public static ErrorCode publicError(ErrorCode original) {
        var attempt = current();
        if (attempt == null) return original;
        attempt.failure(original);
        if (original == ErrorCode.LOGIN_CONFIRMATION_REQUIRED) return original;
        if (original == ErrorCode.INVALID_CREDENTIALS && attempt.credentialsMismatch()) return original;
        return ErrorCode.LOGIN_SERVER_ERROR;
    }
}
