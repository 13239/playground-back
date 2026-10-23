package com.project.playground.global.security.handler;

import com.project.playground.global.exception.ErrorCode;
import org.springframework.security.core.AuthenticationException;

/** JWT 인증 필터에서도 제한 안내와 일시적인 정책 장애를 MVC와 같은 오류 계약으로 보낸다. */
public class AccessPolicyAuthenticationException extends AuthenticationException {
    private final ErrorCode errorCode;
    public AccessPolicyAuthenticationException(ErrorCode errorCode, String notice) {
        super(notice);
        this.errorCode = errorCode;
    }
    public ErrorCode getErrorCode() { return errorCode; }
}
