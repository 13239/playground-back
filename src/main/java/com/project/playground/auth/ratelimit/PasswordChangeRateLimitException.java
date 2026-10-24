package com.project.playground.auth.ratelimit;

import com.project.playground.global.exception.BusinessException;
import com.project.playground.global.exception.ErrorCode;
import lombok.Getter;

/** 현재 비밀번호 대입 시도는 로그인 카운터와 분리하고 남은 대기 시간을 전달한다. */
@Getter
public class PasswordChangeRateLimitException extends BusinessException {

    private final long retryAfterSeconds;

    public PasswordChangeRateLimitException(long retryAfterSeconds) {
        super(ErrorCode.PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
